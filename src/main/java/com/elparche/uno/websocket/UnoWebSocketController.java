package com.elparche.uno.websocket;

import com.elparche.uno.game.GestorSalas;
import com.elparche.uno.game.MotorUno;
import com.elparche.uno.game.ResultadoJugada;
import com.elparche.uno.model.SalaUno;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.elparche.uno.model.Jugador;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UnoWebSocketController {

    private final GestorSalas gestorSalas;
    private final SimpMessagingTemplate messagingTemplate;
    private final com.elparche.uno.config.SalaBroadcastPublisher broadcastPublisher;

    @MessageMapping("/uno/{salaId}/iniciar")
    public void iniciarJuego(@DestinationVariable String salaId,
                             @Payload Map<String, String> payload) {
        try {
            String jugadorId = payload.get("jugadorId");
            SalaUno sala = gestorSalas.iniciarJuego(salaId, jugadorId);
            broadcastEstadoSala(sala);
            broadcastMensaje(salaId, "El juego ha comenzado");
            log.info("Juego iniciado en sala {} por {}", salaId, jugadorId);
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
            log.error("Error iniciando juego en sala {}: {}", salaId, e.getMessage());
        }
    }

    @MessageMapping("/uno/{salaId}/jugar")
    public void jugarCarta(@DestinationVariable String salaId,
                           @Payload Map<String, String> payload) {
        try {
            String jugadorId = payload.get("jugadorId");
            String cartaId = payload.get("cartaId");
            String colorElegido = payload.get("colorElegido");

            SalaUno sala = gestorSalas.getSala(salaId);
            if (sala == null) {
                broadcastError(salaId, "Sala no encontrada");
                return;
            }

            ResultadoJugada resultado;
            synchronized (sala) {
                resultado = MotorUno.jugarCarta(sala, jugadorId, cartaId, colorElegido);
            }

            if (resultado.isExitoso()) {
                gestorSalas.guardarSala(sala);
                broadcastEstadoSala(sala);
                broadcastMensaje(salaId, resultado.getMensaje());

                if (resultado.getTipo() == ResultadoJugada.TipoResultado.GANADOR) {
                    broadcastMensaje(salaId, "🏆 " + resultado.getMensaje());
                    gestorSalas.registrarGanador(salaId, resultado.getJugadorUsername());
                }
                log.info("Carta jugada en sala {} por {} — {}", salaId, jugadorId, resultado.getMensaje());
            } else {
                enviarErrorAJugador(salaId, jugadorId, resultado.getMensaje());
            }
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
            log.error("Error jugando carta en sala {}: {}", salaId, e.getMessage());
        }
    }

    @MessageMapping("/uno/{salaId}/robar")
    public void robarCarta(@DestinationVariable String salaId,
                           @Payload Map<String, String> payload) {
        try {
            String jugadorId = payload.get("jugadorId");
            SalaUno sala = gestorSalas.getSala(salaId);

            if (sala == null) {
                broadcastError(salaId, "Sala no encontrada");
                return;
            }

            ResultadoJugada resultado;
            synchronized (sala) {
                resultado = MotorUno.robarCarta(sala, jugadorId);
            }

            if (resultado.isExitoso()) {
                gestorSalas.guardarSala(sala);
                broadcastEstadoSala(sala);
                broadcastMensaje(salaId, resultado.getMensaje());
                log.info("Carta robada en sala {} por {}", salaId, jugadorId);
            } else {
                enviarErrorAJugador(salaId, jugadorId, resultado.getMensaje());
            }
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
            log.error("Error robando carta en sala {}: {}", salaId, e.getMessage());
        }
    }

    @MessageMapping("/uno/{salaId}/uno")
    public void decirUno(@DestinationVariable String salaId,
                         @Payload Map<String, String> payload) {
        try {
            String jugadorId = payload.get("jugadorId");
            SalaUno sala = gestorSalas.getSala(salaId);

            if (sala == null) {
                broadcastError(salaId, "Sala no encontrada");
                return;
            }

            ResultadoJugada resultado;
            synchronized (sala) {
                resultado = MotorUno.decirUno(sala, jugadorId);
            }

            gestorSalas.guardarSala(sala);
            broadcastEstadoSala(sala);
            broadcastMensaje(salaId, resultado.getMensaje());
            log.info("UNO dicho en sala {} por {}", salaId, jugadorId);
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
        }
    }

    @MessageMapping("/uno/{salaId}/reportar")
    public void reportarUno(@DestinationVariable String salaId,
                            @Payload Map<String, String> payload) {
        try {
            String reportadorId = payload.get("jugadorId");
            String reportadoId = payload.get("reportadoId");
            SalaUno sala = gestorSalas.getSala(salaId);

            if (sala == null) {
                broadcastError(salaId, "Sala no encontrada");
                return;
            }

            ResultadoJugada resultado;
            synchronized (sala) {
                resultado = MotorUno.reportarUno(sala, reportadorId, reportadoId);
            }

            gestorSalas.guardarSala(sala);
            broadcastEstadoSala(sala);
            broadcastMensaje(salaId, resultado.getMensaje());
            log.info("Reporte UNO en sala {} — {} reportó a {}",
                    salaId, reportadorId, reportadoId);
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
        }
    }

    @MessageMapping("/uno/{salaId}/chat")
    public void mensajeChat(@DestinationVariable String salaId,
                            @Payload Map<String, String> payload) {
        String username = payload.get("username");
        String mensaje = payload.get("mensaje");

        if (username == null || username.isBlank()) return;
        if (mensaje == null || mensaje.isBlank()) return;
        if (mensaje.length() > 200) mensaje = mensaje.substring(0, 200);

        SalaUno sala = gestorSalas.getSala(salaId);
        if (sala == null) return;

        boolean esJugadorDeSala = sala.getJugadores().stream()
                .anyMatch(j -> j.getUsername().equals(username));
        if (!esJugadorDeSala) return;

        broadcastPublisher.publicarChat(salaId, username, mensaje);

        log.info("Chat sala {} — {}: {}", salaId, username, mensaje);
    }

    private void broadcastEstadoSala(SalaUno sala) {
        broadcastPublisher.publicarEstado(sala.getId(), sala);
    }

    private void broadcastMensaje(String salaId, String mensaje) {
        broadcastPublisher.publicarMensaje(salaId, mensaje);
    }

    @MessageMapping("/uno/{salaId}/cambiarIcono")
    public void cambiarIcono(@DestinationVariable String salaId,
                             @Payload Map<String, String> payload) {
        try {
            String jugadorId = payload.get("jugadorId");
            String icono = payload.get("icono");
            SalaUno sala = gestorSalas.getSala(salaId);

            if (sala == null) {
                broadcastError(salaId, "Sala no encontrada");
                return;
            }

            synchronized (sala) {
                Jugador jugador = sala.getJugadorById(jugadorId);
                if (jugador != null) {
                    jugador.setIcono(icono);
                }
            }

            gestorSalas.guardarSala(sala);
            broadcastEstadoSala(sala);
            log.info("Icono cambiado en sala {} — {} ahora es {}", salaId, jugadorId, icono);
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
        }
    }

    private void broadcastError(String salaId, String error) {
        broadcastPublisher.publicarError(salaId, error);
    }

    private void enviarErrorAJugador(String salaId, String jugadorId, String error) {
        messagingTemplate.convertAndSendToUser(
                jugadorId,
                "/queue/uno/" + salaId + "/error",
                Map.of("error", error));
    }
}