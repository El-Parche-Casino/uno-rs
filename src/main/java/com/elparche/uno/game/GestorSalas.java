package com.elparche.uno.game;

import com.elparche.uno.config.RedisEventPublisher;
import com.elparche.uno.model.Jugador;
import com.elparche.uno.model.SalaUno;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class GestorSalas {

    private final Map<String, SalaUno> salas = new ConcurrentHashMap<>();
    private final RedisEventPublisher redisEventPublisher;

    public SalaUno crearSala(String nombre,
                             String creadorId,
                             String creadorUsername,
                             int maxJugadores,
                             SalaUno.TipoSala tipo,
                             Double apuestaPorJugador) {

        String salaId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String codigo = tipo == SalaUno.TipoSala.PRIVADA
                ? UUID.randomUUID().toString().substring(0, 6).toUpperCase()
                : null;

        Jugador creador = Jugador.builder()
                .id(creadorId)
                .username(creadorUsername)
                .conectado(true)
                .build();

        SalaUno sala = SalaUno.builder()
                .id(salaId)
                .nombre(nombre)
                .estado(SalaUno.EstadoSala.ESPERANDO)
                .tipo(tipo)
                .codigo(codigo)
                .maxJugadores(maxJugadores)
                .creadorId(creadorId)
                .apuestaPorJugador(apuestaPorJugador)
                .pozoTotal(apuestaPorJugador)
                .jugadores(new ArrayList<>())
                .build();

        sala.getJugadores().add(creador);
        salas.put(salaId, sala);

        redisEventPublisher.publicarApuesta(creadorUsername, apuestaPorJugador, salaId);
        log.info("Sala creada: {} por {}", salaId, creadorUsername);
        return sala;
    }

    public SalaUno unirseASala(String salaId,
                               String jugadorId,
                               String jugadorUsername,
                               String codigo) {

        SalaUno sala = salas.get(salaId);

        if (sala == null) throw new RuntimeException("Sala no encontrada");
        if (sala.estaLlena()) throw new RuntimeException("La sala está llena");
        if (sala.getEstado() != SalaUno.EstadoSala.ESPERANDO)
            throw new RuntimeException("La partida ya comenzó");

        if (sala.getTipo() == SalaUno.TipoSala.PRIVADA) {
            if (!sala.getCodigo().equals(codigo))
                throw new RuntimeException("Código de sala incorrecto");
        }

        boolean yaEsta = sala.getJugadores().stream()
                .anyMatch(j -> j.getId().equals(jugadorId));

        if (!yaEsta) {
            Jugador jugador = Jugador.builder()
                    .id(jugadorId)
                    .username(jugadorUsername)
                    .conectado(true)
                    .build();
            sala.getJugadores().add(jugador);
            sala.setPozoTotal(sala.getPozoTotal() + sala.getApuestaPorJugador());
            redisEventPublisher.publicarApuesta(
                    jugadorUsername, sala.getApuestaPorJugador(), salaId);
        }

        log.info("{} se unió a la sala {}", jugadorUsername, salaId);
        return sala;
    }

    public SalaUno iniciarJuego(String salaId, String solicitanteId) {
        SalaUno sala = salas.get(salaId);
        if (sala == null) throw new RuntimeException("Sala no encontrada");
        if (!sala.getCreadorId().equals(solicitanteId))
            throw new RuntimeException("Solo el creador puede iniciar el juego");
        if (!sala.tieneMinJugadores())
            throw new RuntimeException("Se necesitan mínimo 2 jugadores");

        SalaUno salaIniciada = MotorUno.iniciarJuego(sala);
        salas.put(salaId, salaIniciada);
        return salaIniciada;
    }

    public void registrarGanador(String salaId, String ganadorUsername) {
        SalaUno sala = salas.get(salaId);
        if (sala != null) {
            redisEventPublisher.publicarGanancia(
                    ganadorUsername, sala.getPozoTotal(), salaId);
            log.info("Ganador: {} — pozo: {}", ganadorUsername, sala.getPozoTotal());
        }
    }

    public void devolverApuestas(String salaId) {
        SalaUno sala = salas.get(salaId);
        if (sala != null) {
            sala.getJugadores().forEach(j ->
                    redisEventPublisher.publicarDevolucion(
                            j.getUsername(), sala.getApuestaPorJugador(), salaId)
            );
            log.info("Apuestas devueltas en sala {}", salaId);
        }
    }

    public SalaUno getSala(String salaId) {
        return salas.get(salaId);
    }

    public Collection<SalaUno> getSalasPublicas() {
        return salas.values().stream()
                .filter(s -> s.getTipo() == SalaUno.TipoSala.PUBLICA
                        && s.getEstado() == SalaUno.EstadoSala.ESPERANDO)
                .toList();
    }

    public void jugadorDesconectado(String salaId, String jugadorId) {
        SalaUno sala = salas.get(salaId);
        if (sala != null) {
            Jugador jugador = sala.getJugadorById(jugadorId);
            if (jugador != null) {
                jugador.setConectado(false);
                log.info("Jugador {} desconectado de sala {}", jugadorId, salaId);
            }
        }
    }

    public void eliminarSala(String salaId) {
        salas.remove(salaId);
        log.info("Sala {} eliminada", salaId);
    }
}