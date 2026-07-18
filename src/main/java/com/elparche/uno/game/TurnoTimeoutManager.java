package com.elparche.uno.game;

import com.elparche.uno.config.SalaBroadcastPublisher;
import com.elparche.uno.model.Jugador;
import com.elparche.uno.model.SalaUno;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TurnoTimeoutManager {

    private static final long TIMEOUT_SEGUNDOS = 30;
    private static final int CARTAS_PENALIZACION = 2;
    private static final int RECARGO_NO_RESPONDER = 2;
    private static final String REDIS_NONCE_PREFIX = "timeout:uno:";

    private final GestorSalas gestorSalas;
    private final SalaBroadcastPublisher broadcastPublisher;
    private final RedisTemplate<String, String> redisTemplate;

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "turno-timeout-uno");
                t.setDaemon(true);
                return t;
            });

    private final Map<String, ScheduledFuture<?>> tareas = new ConcurrentHashMap<>();

    // El nonce en Redis es la fuente de verdad compartida entre instancias: cada
    // acción que reprograma escribe uno nuevo, y la tarea programada solo actúa
    // si el suyo sigue vigente al disparar — así los timers huérfanos de otra
    // instancia (que ya no tienen forma de cancelarse localmente) se descartan
    // solos en vez de penalizar contra estado desactualizado.
    public void reprogramar(SalaUno sala) {
        cancelarTareaLocal(sala.getId());

        if (sala.getEstado() != SalaUno.EstadoSala.EN_JUEGO
                || sala.getJugadorActualId() == null) {
            borrarNonce(sala.getId());
            return;
        }

        String salaId = sala.getId();
        String nonce = UUID.randomUUID().toString();
        try {
            redisTemplate.opsForValue().set(
                    REDIS_NONCE_PREFIX + salaId, nonce, 10, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("No se pudo guardar el nonce de timeout de la sala {}: {}",
                    salaId, e.getMessage());
            return;
        }

        ScheduledFuture<?> tarea = scheduler.schedule(
                () -> penalizarPorInactividad(salaId, nonce),
                TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        tareas.put(salaId, tarea);
        broadcastPublisher.publicarTimer(salaId, TIMEOUT_SEGUNDOS);
    }

    public void cancelar(String salaId) {
        cancelarTareaLocal(salaId);
        borrarNonce(salaId);
    }

    private void cancelarTareaLocal(String salaId) {
        ScheduledFuture<?> anterior = tareas.remove(salaId);
        if (anterior != null) {
            anterior.cancel(false);
        }
    }

    private void borrarNonce(String salaId) {
        try {
            redisTemplate.delete(REDIS_NONCE_PREFIX + salaId);
        } catch (Exception e) {
            log.error("No se pudo borrar el nonce de timeout de la sala {}: {}",
                    salaId, e.getMessage());
        }
    }

    private String nonceVigente(String salaId) {
        try {
            return redisTemplate.opsForValue().get(REDIS_NONCE_PREFIX + salaId);
        } catch (Exception e) {
            return null;
        }
    }

    private void penalizarPorInactividad(String salaId, String nonce) {
        try {
            tareas.remove(salaId);
            if (!nonce.equals(nonceVigente(salaId))) {
                // Otra instancia ya reprogramó (o canceló) este turno: este timer
                // quedó huérfano y no debe actuar.
                return;
            }
            // Solo Redis, nunca el fallback de memoria local: si la sala ya no
            // existe no hay nada que penalizar (evita resucitar salas eliminadas).
            SalaUno sala = gestorSalas.obtenerSalaDesdeRedis(salaId);
            if (sala == null) {
                borrarNonce(salaId);
                return;
            }

            synchronized (sala) {
                if (sala.getEstado() != SalaUno.EstadoSala.EN_JUEGO
                        || sala.getJugadorActualId() == null) {
                    borrarNonce(salaId);
                    return;
                }
                Jugador jugador = sala.getJugadorById(sala.getJugadorActualId());
                if (jugador == null) return;

                String mensaje;
                if (jugador.isDebeResponderPenalizacion()) {
                    int acumulada = sala.getPenalizacionAcumulada();
                    if (acumulada <= 0) acumulada = 2;

                    // robarCarta ya cobra la penalización acumulada, limpia el
                    // estado de "debe responder" y pasa el turno.
                    MotorUno.robarCarta(sala, jugador.getId());

                    // Recargo por no responder a tiempo, además de lo acumulado.
                    int recargo = 0;
                    for (int i = 0; i < RECARGO_NO_RESPONDER; i++) {
                        try {
                            jugador.agregarCarta(
                                    MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte()));
                            recargo++;
                        } catch (RuntimeException e) {
                            break;
                        }
                    }

                    mensaje = "⏰ " + jugador.getUsername() + " no respondió en "
                            + TIMEOUT_SEGUNDOS + "s y comió " + acumulada + "+" + recargo
                            + " = " + (acumulada + recargo)
                            + " cartas por penalización y recargo";
                } else {
                    int robadas = 0;
                    for (int i = 0; i < CARTAS_PENALIZACION; i++) {
                        try {
                            jugador.agregarCarta(
                                    MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte()));
                            robadas++;
                        } catch (RuntimeException e) {
                            break;
                        }
                    }
                    MotorUno.siguienteTurno(sala);
                    mensaje = "⏰ " + jugador.getUsername() + " no jugó en " + TIMEOUT_SEGUNDOS
                            + "s — +" + robadas + " cartas y pierde el turno";
                }

                gestorSalas.guardarSala(sala);
                broadcastPublisher.publicarEstado(salaId, sala);
                broadcastPublisher.publicarMensaje(salaId, mensaje);
                log.info("Timeout de turno en sala {}: {}", salaId, mensaje);
                reprogramar(sala);
            }
        } catch (Exception e) {
            log.error("Error aplicando timeout de turno en sala {}: {}", salaId, e.getMessage());
        }
    }
}

