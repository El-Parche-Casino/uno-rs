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

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class UnoWebSocketController {

    private final GestorSalas gestorSalas;
    private final SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/uno/{salaId}/iniciar")
    public void iniciarJuego(@DestinationVariable String salaId,
                             @Payload Map<String, String> payload) {
        try {
            String jugadorId = payload.get("jugadorId");
            SalaUno sala = gestorSalas.iniciarJuego(salaId, jugadorId);
            broadcastEstadoSala(sala);
            broadcastMensaje(salaId, "El juego ha comenzado");
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
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

            ResultadoJugada resultado = MotorUno.jugarCarta(
                    sala, jugadorId, cartaId, colorElegido);

            if (resultado.isExitoso()) {
                broadcastEstadoSala(sala);
                broadcastMensaje(salaId, resultado.getMensaje());

                if (resultado.getTipo() == ResultadoJugada.TipoResultado.GANADOR) {
                    broadcastMensaje(salaId, "🎉 " + resultado.getMensaje());
                }
            } else {
                enviarErrorAJugador(salaId, jugadorId, resultado.getMensaje());
            }
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
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

            ResultadoJugada resultado = MotorUno.robarCarta(sala, jugadorId);

            if (resultado.isExitoso()) {
                broadcastEstadoSala(sala);
                broadcastMensaje(salaId, resultado.getMensaje());
            } else {
                enviarErrorAJugador(salaId, jugadorId, resultado.getMensaje());
            }
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
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

            ResultadoJugada resultado = MotorUno.decirUno(sala, jugadorId);
            broadcastEstadoSala(sala);
            broadcastMensaje(salaId, resultado.getMensaje());
        } catch (Exception e) {
            broadcastError(salaId, e.getMessage());
        }
    }

    private void broadcastEstadoSala(SalaUno sala) {
        messagingTemplate.convertAndSend(
                "/topic/uno/" + sala.getId() + "/estado", sala);
    }

    private void broadcastMensaje(String salaId, String mensaje) {
        messagingTemplate.convertAndSend(
                "/topic/uno/" + salaId + "/mensajes",
                Map.of("mensaje", mensaje));
    }

    private void broadcastError(String salaId, String error) {
        messagingTemplate.convertAndSend(
                "/topic/uno/" + salaId + "/errores",
                Map.of("error", error));
    }

    private void enviarErrorAJugador(String salaId, String jugadorId, String error) {
        messagingTemplate.convertAndSendToUser(
                jugadorId,
                "/queue/uno/" + salaId + "/error",
                Map.of("error", error));
    }
}