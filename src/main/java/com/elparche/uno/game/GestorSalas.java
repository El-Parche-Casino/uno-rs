package com.elparche.uno.game;

import com.elparche.uno.config.RedisEventPublisher;
import com.elparche.uno.model.Jugador;
import com.elparche.uno.model.SalaUno;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GestorSalas {

    private final Map<String, SalaUno> salas = new ConcurrentHashMap<>();
    private final RedisEventPublisher redisEventPublisher;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String REDIS_SALA_PREFIX = "sala:uno:";
    private static final long SALA_TTL_HOURS = 4;

    public GestorSalas(RedisEventPublisher redisEventPublisher,
                       RedisTemplate<String, String> redisTemplate) {
        this.redisEventPublisher = redisEventPublisher;
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        recuperarSalasDeRedis();
    }

    private void guardarEnRedis(SalaUno sala) {
        try {
            String json = objectMapper.writeValueAsString(sala);
            redisTemplate.opsForValue().set(
                    REDIS_SALA_PREFIX + sala.getId(),
                    json,
                    SALA_TTL_HOURS,
                    TimeUnit.HOURS
            );
            log.info("Sala {} guardada en Redis", sala.getId());
        } catch (JsonProcessingException e) {
            log.error("Error guardando sala en Redis: {}", e.getMessage());
        }
    }

    private void eliminarDeRedis(String salaId) {
        redisTemplate.delete(REDIS_SALA_PREFIX + salaId);
    }

    private void recuperarSalasDeRedis() {
        try {
            var keys = redisTemplate.keys(REDIS_SALA_PREFIX + "*");
            if (keys == null || keys.isEmpty()) {
                log.info("No hay salas para recuperar de Redis");
                return;
            }
            for (String key : keys) {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    SalaUno sala = objectMapper.readValue(json, SalaUno.class);
                    salas.put(sala.getId(), sala);
                    log.info("Sala {} recuperada de Redis", sala.getId());
                }
            }
            log.info("{} salas recuperadas de Redis al arrancar", salas.size());
        } catch (Exception e) {
            log.warn("Error recuperando salas de Redis: {}", e.getMessage());
        }
    }

    public SalaUno crearSala(String nombre,
                             String creadorId,
                             String creadorUsername,
                             String icono,
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
                .icono(icono != null ? icono : "🦁")
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
        guardarEnRedis(sala);

        redisEventPublisher.publicarApuesta(creadorUsername, apuestaPorJugador, salaId);
        log.info("Sala creada: {} por {} — tipo: {} — apuesta: {}",
                salaId, creadorUsername, tipo, apuestaPorJugador);
        return sala;
    }

    public SalaUno unirseASala(String salaId,
                               String jugadorId,
                               String jugadorUsername,
                               String icono,
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

        synchronized (sala) {
            boolean yaEsta = sala.getJugadores().stream()
                    .anyMatch(j -> j.getId().equals(jugadorId));

            if (!yaEsta) {
                Jugador jugador = Jugador.builder()
                        .id(jugadorId)
                        .username(jugadorUsername)
                        .icono(icono != null ? icono : "🦊")
                        .conectado(true)
                        .build();
                sala.getJugadores().add(jugador);
                sala.setPozoTotal(sala.getPozoTotal() + sala.getApuestaPorJugador());
                redisEventPublisher.publicarApuesta(
                        jugadorUsername, sala.getApuestaPorJugador(), salaId);
            }
            guardarEnRedis(sala);
        }

        log.info("{} se unió a la sala {} — jugadores: {}/{}",
                jugadorUsername, salaId, sala.getJugadores().size(), sala.getMaxJugadores());
        return sala;
    }

    public SalaUno iniciarJuego(String salaId, String solicitanteId) {
        SalaUno sala = salas.get(salaId);
        if (sala == null) throw new RuntimeException("Sala no encontrada");
        if (!sala.getCreadorId().equals(solicitanteId))
            throw new RuntimeException("Solo el creador puede iniciar el juego");
        if (!sala.tieneMinJugadores())
            throw new RuntimeException("Se necesitan mínimo 2 jugadores");

        synchronized (sala) {
            SalaUno salaIniciada = MotorUno.iniciarJuego(sala);
            salas.put(salaId, salaIniciada);
            guardarEnRedis(salaIniciada);
            return salaIniciada;
        }
    }

    public SalaUno jugarCarta(String salaId, String jugadorId,
                              String cartaId, String colorElegido) {
        SalaUno sala = salas.get(salaId);
        if (sala == null) throw new RuntimeException("Sala no encontrada");

        synchronized (sala) {
            ResultadoJugada resultado = MotorUno.jugarCarta(sala, jugadorId, cartaId, colorElegido);
            if (resultado.isExitoso()) {
                guardarEnRedis(sala);
                if (resultado.getTipo() == ResultadoJugada.TipoResultado.GANADOR) {
                    registrarGanador(salaId, resultado.getJugadorUsername());
                }
            }
            return sala;
        }
    }

    public SalaUno robarCartaDeSala(String salaId, String jugadorId) {
        SalaUno sala = salas.get(salaId);
        if (sala == null) throw new RuntimeException("Sala no encontrada");

        synchronized (sala) {
            ResultadoJugada resultado = MotorUno.robarCarta(sala, jugadorId);
            if (resultado.isExitoso()) {
                guardarEnRedis(sala);
            }
            return sala;
        }
    }

    public void registrarGanador(String salaId, String ganadorUsername) {
        SalaUno sala = salas.get(salaId);
        if (sala != null) {
            redisEventPublisher.publicarGanancia(
                    ganadorUsername, sala.getPozoTotal(), salaId);
            log.info("Ganador: {} — pozo: {} fichas — sala: {}",
                    ganadorUsername, sala.getPozoTotal(), salaId);
            eliminarDeRedis(salaId);
        }
    }

    public void devolverApuestas(String salaId) {
        SalaUno sala = salas.get(salaId);
        if (sala != null) {
            sala.getJugadores().forEach(j ->
                    redisEventPublisher.publicarDevolucion(
                            j.getUsername(), sala.getApuestaPorJugador(), salaId)
            );
            log.info("Apuestas devueltas en sala {} — {} jugadores",
                    salaId, sala.getJugadores().size());
            eliminarDeRedis(salaId);
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
            synchronized (sala) {
                Jugador jugador = sala.getJugadorById(jugadorId);
                if (jugador != null) {
                    jugador.setConectado(false);
                    guardarEnRedis(sala);
                    log.info("Jugador {} desconectado de sala {}", jugadorId, salaId);
                }
            }
        }
    }

    public void eliminarSala(String salaId) {
        salas.remove(salaId);
        eliminarDeRedis(salaId);
        log.info("Sala {} eliminada", salaId);
    }
}