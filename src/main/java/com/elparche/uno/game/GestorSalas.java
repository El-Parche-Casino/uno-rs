package com.elparche.uno.game;

import com.elparche.uno.config.RedisEventPublisher;
import com.elparche.uno.config.WalletClient;
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
    private final WalletClient walletClient;
    private final ObjectMapper objectMapper;

    private static final String REDIS_SALA_PREFIX = "sala:uno:";
    private static final long SALA_TTL_HOURS = 4;

    public GestorSalas(RedisEventPublisher redisEventPublisher,
                       RedisTemplate<String, String> redisTemplate,
                       WalletClient walletClient) {
        this.redisEventPublisher = redisEventPublisher;
        this.redisTemplate = redisTemplate;
        this.walletClient = walletClient;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
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

    private SalaUno obtenerSala(String salaId) {
        String json = redisTemplate.opsForValue().get(REDIS_SALA_PREFIX + salaId);
        if (json != null) {
            try {
                SalaUno sala = objectMapper.readValue(json, SalaUno.class);
                salas.put(salaId, sala);
                return sala;
            } catch (JsonProcessingException e) {
                log.error("Error leyendo sala {} desde Redis: {}", salaId, e.getMessage());
            }
        }
        return salas.get(salaId);
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

        validarSaldo(creadorUsername, apuestaPorJugador);

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

        SalaUno sala = obtenerSala(salaId);

        if (sala == null) throw new RuntimeException("Sala no encontrada");
        if (sala.estaLlena()) throw new RuntimeException("La sala está llena");
        if (sala.getEstado() != SalaUno.EstadoSala.ESPERANDO)
            throw new RuntimeException("La partida ya comenzó");

        if (sala.getTipo() == SalaUno.TipoSala.PRIVADA) {
            if (!sala.getCodigo().equals(codigo))
                throw new RuntimeException("Código de sala incorrecto");
        }

        boolean estabaEnSala = sala.getJugadores().stream()
                .anyMatch(j -> j.getId().equals(jugadorId));
        if (!estabaEnSala) {
            validarSaldo(jugadorUsername, sala.getApuestaPorJugador());
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

    public SalaUno unirsePorCodigo(String codigo,
                                   String jugadorId,
                                   String jugadorUsername,
                                   String icono) {
        if (codigo == null || codigo.isBlank())
            throw new RuntimeException("Debes ingresar el código de la sala");
        String normalizado = codigo.trim().toUpperCase();

        SalaUno encontrada = null;
        try {
            var claves = redisTemplate.keys(REDIS_SALA_PREFIX + "*");
            if (claves != null) {
                encontrada = claves.stream()
                        .map(k -> obtenerSala(k.substring(REDIS_SALA_PREFIX.length())))
                        .filter(java.util.Objects::nonNull)
                        .filter(s -> normalizado.equals(s.getCodigo()))
                        .findFirst().orElse(null);
            }
        } catch (Exception e) {
            log.error("Error buscando sala por código en Redis: {}", e.getMessage());
        }
        if (encontrada == null) {
            encontrada = salas.values().stream()
                    .filter(s -> normalizado.equals(s.getCodigo()))
                    .findFirst().orElse(null);
        }
        if (encontrada == null)
            throw new RuntimeException("No existe una sala con ese código");

        return unirseASala(encontrada.getId(), jugadorId, jugadorUsername, icono, normalizado);
    }

    private void validarSaldo(String username, Double apuesta) {
        if (apuesta == null || apuesta <= 0) return;
        Double saldo = walletClient.consultarSaldo(username);
        if (saldo == null)
            throw new RuntimeException("No se pudo verificar tu saldo, intenta de nuevo");
        if (apuesta > saldo)
            throw new RuntimeException("Saldo insuficiente: tienes " + formatearFichas(saldo)
                    + " fichas y la apuesta es " + formatearFichas(apuesta));
    }

    private static String formatearFichas(double valor) {
        return valor == Math.floor(valor)
                ? String.valueOf((long) valor)
                : String.valueOf(valor);
    }

    public SalaUno iniciarJuego(String salaId, String solicitanteId) {
        SalaUno sala = obtenerSala(salaId);
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
        SalaUno sala = obtenerSala(salaId);
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
        SalaUno sala = obtenerSala(salaId);
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
        SalaUno sala = obtenerSala(salaId);
        if (sala != null) {
            redisEventPublisher.publicarGanancia(
                    ganadorUsername, sala.getPozoTotal(), salaId);
            log.info("Ganador: {} — pozo: {} fichas — sala: {}",
                    ganadorUsername, sala.getPozoTotal(), salaId);
            eliminarDeRedis(salaId);
        }
    }

    public void devolverApuestas(String salaId) {
        SalaUno sala = obtenerSala(salaId);
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
        return obtenerSala(salaId);
    }

    // Solo Redis, sin fallback a memoria local: para el path del timeout, que no
    // debe resucitar salas ya eliminadas.
    public SalaUno obtenerSalaDesdeRedis(String salaId) {
        String json = redisTemplate.opsForValue().get(REDIS_SALA_PREFIX + salaId);
        if (json == null) return null;
        try {
            SalaUno sala = objectMapper.readValue(json, SalaUno.class);
            salas.put(salaId, sala);
            return sala;
        } catch (JsonProcessingException e) {
            log.error("Error leyendo sala {} desde Redis: {}", salaId, e.getMessage());
            return null;
        }
    }

    public java.util.List<Map<String, Object>> listarSalasActivas() {
        java.util.List<Map<String, Object>> lista = new ArrayList<>();
        java.util.Set<String> claves = redisTemplate.keys(REDIS_SALA_PREFIX + "*");
        if (claves == null) return lista;
        for (String clave : claves) {
            try {
                String json = redisTemplate.opsForValue().get(clave);
                if (json == null) continue;
                SalaUno sala = objectMapper.readValue(json, SalaUno.class);
                if (sala.getEstado() == SalaUno.EstadoSala.TERMINADA) continue;
                Map<String, Object> item = new java.util.LinkedHashMap<>();
                item.put("id", sala.getId());
                item.put("nombre", sala.getNombre());
                item.put("jugadores", sala.getJugadores() != null ? sala.getJugadores().size() : 0);
                item.put("estado", sala.getEstado().name());
                lista.add(item);
            } catch (Exception e) {
                log.warn("No se pudo leer la sala {} para el listado admin: {}", clave, e.getMessage());
            }
        }
        return lista;
    }

    public Collection<SalaUno> getSalasPublicas() {
        try {
            var keys = redisTemplate.keys(REDIS_SALA_PREFIX + "*");
            if (keys != null) {
                return keys.stream()
                        .map(k -> obtenerSala(k.substring(REDIS_SALA_PREFIX.length())))
                        .filter(java.util.Objects::nonNull)
                        .filter(s -> s.getTipo() == SalaUno.TipoSala.PUBLICA
                                && s.getEstado() == SalaUno.EstadoSala.ESPERANDO
                                && !s.getJugadores().isEmpty())
                        .toList();
            }
        } catch (Exception e) {
            log.error("Error listando salas desde Redis, usando memoria local: {}", e.getMessage());
        }
        return salas.values().stream()
                .filter(s -> s.getTipo() == SalaUno.TipoSala.PUBLICA
                        && s.getEstado() == SalaUno.EstadoSala.ESPERANDO
                        && !s.getJugadores().isEmpty())
                .toList();
    }

    public void jugadorDesconectado(String salaId, String jugadorId) {
        SalaUno sala = obtenerSala(salaId);
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

    public record ResultadoReinicio(boolean reiniciada, java.util.List<String> excluidos, String error) {
    }

    public ResultadoReinicio reiniciarSala(String salaId) {
        SalaUno sala = obtenerSala(salaId);
        if (sala == null) throw new RuntimeException("Sala no encontrada");

        java.util.List<String> excluidos = new ArrayList<>();
        synchronized (sala) {
            if (sala.getEstado() != SalaUno.EstadoSala.TERMINADA) {
                log.info("Reinicio ignorado en sala {}: estado {} (solo se reinicia una sala TERMINADA)",
                        salaId, sala.getEstado());
                return new ResultadoReinicio(false, java.util.List.of(), null);
            }

            Double apuesta = sala.getApuestaPorJugador();
            java.util.List<Jugador> sinSaldo = new ArrayList<>();
            if (apuesta != null && apuesta > 0) {
                for (Jugador jugador : sala.getJugadores()) {
                    Double saldo = walletClient.consultarSaldo(jugador.getUsername());
                    if (saldo == null) {
                        log.info("Reinicio rechazado en sala {}: wallet no disponible para verificar saldos", salaId);
                        return new ResultadoReinicio(false, java.util.List.of(),
                                "No se pudo verificar el saldo de los jugadores, intenta de nuevo");
                    }
                    if (apuesta > saldo) {
                        sinSaldo.add(jugador);
                    }
                }
            }

            if (!sinSaldo.isEmpty() && sala.getJugadores().size() - sinSaldo.size() < 2) {
                String nombres = sinSaldo.stream().map(Jugador::getUsername)
                        .collect(java.util.stream.Collectors.joining(", "));
                log.info("Reinicio rechazado en sala {}: sin saldo suficiente [{}] y quedarian menos de 2 jugadores",
                        salaId, nombres);
                return new ResultadoReinicio(false, java.util.List.of(),
                        "No se puede reiniciar: " + nombres + " no tiene saldo suficiente para la apuesta"
                                + " y quedarian menos de 2 jugadores en la sala");
            }

            for (Jugador jugador : sinSaldo) {
                sala.getJugadores().removeIf(j -> j.getId().equals(jugador.getId()));
                excluidos.add(jugador.getUsername());
                log.info("Jugador {} excluido de la sala {} al reiniciar: saldo insuficiente para la apuesta de {}",
                        jugador.getUsername(), salaId, apuesta);
            }
            if (!excluidos.isEmpty() && sala.getJugadorById(sala.getCreadorId()) == null) {
                sala.setCreadorId(sala.getJugadores().get(0).getId());
                log.info("Liderazgo de sala {} transferido a {}", salaId, sala.getCreadorId());
            }

            sala.setEstado(SalaUno.EstadoSala.ESPERANDO);
            sala.setMazo(new ArrayList<>());
            sala.setPillaDescarte(new ArrayList<>());
            sala.setCartaActual(null);
            sala.setJugadorActualId(null);
            sala.setSentidoHorario(true);
            sala.setColorActual(null);
            sala.setTipoPenalizacion(null);
            sala.setPenalizacionAcumulada(0);
            sala.setPozoTotal(sala.getApuestaPorJugador() * sala.getJugadores().size());

            for (Jugador jugador : sala.getJugadores()) {
                jugador.setMano(new ArrayList<>());
                jugador.setDijoUno(false);
                jugador.setPenalizadoUno(false);
                jugador.setCartasRobadas(0);
                jugador.setDebeResponderPenalizacion(false);
            }

            if (sala.getApuestaPorJugador() != null && sala.getApuestaPorJugador() > 0) {
                for (Jugador jugador : sala.getJugadores()) {
                    redisEventPublisher.publicarApuesta(
                            jugador.getUsername(), sala.getApuestaPorJugador(), salaId);
                }
            }

            guardarSala(sala);
        }
        log.info("Sala {} reiniciada — vuelve a ESPERANDO y se cobraron las apuestas de la nueva ronda", salaId);
        return new ResultadoReinicio(true, excluidos, null);
    }

    public SalaUno salirDeSala(String salaId, String jugadorId) {
        SalaUno sala = obtenerSala(salaId);
        if (sala == null) return null;

        synchronized (sala) {
            Jugador saliente = sala.getJugadorById(jugadorId);
            boolean estaba = sala.getJugadores().removeIf(j -> j.getId().equals(jugadorId));
            if (!estaba) return sala;

            if (sala.getEstado() == SalaUno.EstadoSala.ESPERANDO
                    && sala.getApuestaPorJugador() != null
                    && sala.getApuestaPorJugador() > 0
                    && saliente != null) {
                sala.setPozoTotal(Math.max(0, sala.getPozoTotal() - sala.getApuestaPorJugador()));
                redisEventPublisher.publicarDevolucion(
                        saliente.getUsername(), sala.getApuestaPorJugador(), salaId);
                log.info("Apuesta devuelta a {} al salir de la sala {} en espera — pozo queda en {}",
                        saliente.getUsername(), salaId, sala.getPozoTotal());
            }

            if (sala.getJugadores().isEmpty()) {
                eliminarSala(salaId);
                return null;
            }

            if (jugadorId.equals(sala.getCreadorId())) {
                sala.setCreadorId(sala.getJugadores().get(0).getId());
                log.info("Liderazgo de sala {} transferido a {}", salaId, sala.getCreadorId());
            }

            if (sala.getEstado() == SalaUno.EstadoSala.EN_JUEGO
                    && sala.getJugadorActualId() != null
                    && sala.getJugadorById(sala.getJugadorActualId()) == null) {
                sala.setJugadorActualId(sala.getJugadores().get(0).getId());
            }

            guardarSala(sala);
        }
        log.info("Jugador {} salio de la sala {} — quedan {}",
                jugadorId, salaId, sala.getJugadores().size());
        return sala;
    }

    public void eliminarSala(String salaId) {
        salas.remove(salaId);
        eliminarDeRedis(salaId);
        log.info("Sala {} eliminada", salaId);
    }

    public void guardarSala(SalaUno sala) {
        salas.put(sala.getId(), sala);
        guardarEnRedis(sala);
    }
}