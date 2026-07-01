package com.elparche.uno.game;

import com.elparche.uno.model.Carta;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResultadoJugada {

    private boolean exitoso;
    private String mensaje;
    private String jugadorUsername;
    private Carta cartaJugada;
    private TipoResultado tipo;

    public enum TipoResultado {
        EXITO, ERROR, GANADOR, ROBO
    }

    public static ResultadoJugada exito(String username, Carta carta, String mensaje) {
        return ResultadoJugada.builder()
                .exitoso(true)
                .jugadorUsername(username)
                .cartaJugada(carta)
                .mensaje(mensaje)
                .tipo(TipoResultado.EXITO)
                .build();
    }

    public static ResultadoJugada error(String mensaje) {
        return ResultadoJugada.builder()
                .exitoso(false)
                .mensaje(mensaje)
                .tipo(TipoResultado.ERROR)
                .build();
    }

    public static ResultadoJugada ganador(String username) {
        return ResultadoJugada.builder()
                .exitoso(true)
                .jugadorUsername(username)
                .mensaje(username + " ganó la partida")
                .tipo(TipoResultado.GANADOR)
                .build();
    }

    public static ResultadoJugada robo(String username, Carta carta) {
        return ResultadoJugada.builder()
                .exitoso(true)
                .jugadorUsername(username)
                .cartaJugada(carta)
                .mensaje(username + " robó una carta")
                .tipo(TipoResultado.ROBO)
                .build();
    }
}