package com.elparche.uno.game;

import com.elparche.uno.model.Carta;
import com.elparche.uno.model.Jugador;
import com.elparche.uno.model.SalaUno;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

@Slf4j
public class MotorUno {

    private static final int CARTAS_INICIALES = 7;

    public static SalaUno iniciarJuego(SalaUno sala) {
        if (!sala.tieneMinJugadores()) {
            throw new RuntimeException("Se necesitan mínimo 2 jugadores para iniciar");
        }

        List<Carta> mazo = MazoUno.crearMazo();
        List<Carta> pillaDescarte = new ArrayList<>();

        for (Jugador jugador : sala.getJugadores()) {
            jugador.setMano(MazoUno.repartirMano(mazo, pillaDescarte, CARTAS_INICIALES));
        }

        Carta primera = null;
        while (primera == null || primera.esComodin()) {
            primera = MazoUno.robarCarta(mazo, pillaDescarte);
        }
        pillaDescarte.add(primera);

        sala.setMazo(mazo);
        sala.setPillaDescarte(pillaDescarte);
        sala.setCartaActual(primera);
        sala.setColorActual(primera.getColor().name());
        sala.setSentidoHorario(true);
        sala.setEstado(SalaUno.EstadoSala.EN_JUEGO);

        sala.setJugadorActualId(sala.getJugadores().get(0).getId());

        aplicarEfectoPrimeraCarta(sala);

        log.info("Juego iniciado en sala {} con {} jugadores",
                sala.getId(), sala.getJugadores().size());
        return sala;
    }

    public static ResultadoJugada jugarCarta(SalaUno sala,
                                             String jugadorId,
                                             String cartaId,
                                             String colorElegido) {

        if (!sala.getJugadorActualId().equals(jugadorId)) {
            return ResultadoJugada.error("No es tu turno");
        }

        Jugador jugador = sala.getJugadorById(jugadorId);
        if (jugador == null) {
            return ResultadoJugada.error("Jugador no encontrado");
        }

        Carta carta = jugador.getMano().stream()
                .filter(c -> c.getId().equals(cartaId))
                .findFirst()
                .orElse(null);

        if (carta == null) {
            return ResultadoJugada.error("No tienes esa carta");
        }

        if (jugador.isDebeResponderPenalizacion()) {
            String tipoPen = sala.getTipoPenalizacion();
            if ("MAS_DOS".equals(tipoPen)) {
                if (carta.getValor() != Carta.Valor.MAS_DOS && carta.getValor() != Carta.Valor.MAS_CUATRO) {
                    return ResultadoJugada.error(
                            "Debes responder con +2 o +4, o roba " + sala.getPenalizacionAcumulada() + " cartas");
                }
            } else if ("MAS_CUATRO".equals(tipoPen)) {
                if (carta.getValor() != Carta.Valor.MAS_CUATRO) {
                    return ResultadoJugada.error(
                            "Debes responder con +4, o roba " + sala.getPenalizacionAcumulada() + " cartas");
                }
            }
            jugador.setDebeResponderPenalizacion(false);
        } else {
            Carta cartaEfectiva = Carta.builder()
                    .color(Carta.Color.valueOf(sala.getColorActual()))
                    .valor(sala.getCartaActual().getValor())
                    .id(sala.getCartaActual().getId())
                    .build();

            if (!carta.esCompatibleCon(cartaEfectiva)) {
                return ResultadoJugada.error("Esa carta no es compatible con la carta actual");
            }
        }

        if (carta.esComodin() && (colorElegido == null || colorElegido.isEmpty())) {
            return ResultadoJugada.error("Debes elegir un color para el comodín");
        }

        jugador.quitarCarta(carta);
        sala.getPillaDescarte().add(carta);
        sala.setCartaActual(carta);

        if (carta.esComodin()) {
            sala.setColorActual(colorElegido);
        } else {
            sala.setColorActual(carta.getColor().name());
        }

        jugador.setDijoUno(false);

        if (jugador.gano()) {
            sala.setEstado(SalaUno.EstadoSala.TERMINADA);
            sala.setPenalizacionAcumulada(0);
            sala.setTipoPenalizacion(null);
            return ResultadoJugada.ganador(jugador.getUsername());
        }

        String mensaje = aplicarEfecto(sala, carta);
        return ResultadoJugada.exito(jugador.getUsername(), carta, mensaje);
    }

    public static ResultadoJugada robarCarta(SalaUno sala, String jugadorId) {
        if (!sala.getJugadorActualId().equals(jugadorId)) {
            return ResultadoJugada.error("No es tu turno");
        }

        Jugador jugador = sala.getJugadorById(jugadorId);

        if (jugador.isDebeResponderPenalizacion()) {
            int cantidad = sala.getPenalizacionAcumulada();
            if (cantidad <= 0) cantidad = 2;

            for (int i = 0; i < cantidad; i++) {
                jugador.agregarCarta(
                        MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte()));
            }

            String msg = jugador.getUsername() + " comió " + cantidad + " cartas";

            sala.setPenalizacionAcumulada(0);
            sala.setTipoPenalizacion(null);
            jugador.setDebeResponderPenalizacion(false);

            for (Jugador j : sala.getJugadores()) {
                j.setDebeResponderPenalizacion(false);
            }

            siguienteTurno(sala);
            return ResultadoJugada.exito(jugador.getUsername(), null, msg);
        }

        Carta cartaEfectiva = Carta.builder()
                .color(Carta.Color.valueOf(sala.getColorActual()))
                .valor(sala.getCartaActual().getValor())
                .id(sala.getCartaActual().getId())
                .build();

        int robadas = 0;
        Carta cartaJugable = null;
        while (cartaJugable == null) {
            Carta carta;
            try {
                carta = MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte());
            } catch (RuntimeException e) {
                break;
            }
            jugador.agregarCarta(carta);
            robadas++;
            if (carta.esCompatibleCon(cartaEfectiva)) {
                cartaJugable = carta;
            }
        }

        if (cartaJugable == null) {
            siguienteTurno(sala);
            ResultadoJugada resultado = ResultadoJugada.robo(jugador.getUsername(), null);
            resultado.setMensaje(jugador.getUsername() + " robó " + robadas
                    + (robadas == 1 ? " carta" : " cartas")
                    + " y no encontró ninguna jugable — pasa el turno");
            return resultado;
        }

        ResultadoJugada resultado = ResultadoJugada.robo(jugador.getUsername(), cartaJugable);
        resultado.setMensaje(jugador.getUsername() + " robó " + robadas
                + (robadas == 1 ? " carta" : " cartas")
                + " hasta encontrar una jugable");
        return resultado;
    }

    public static ResultadoJugada decirUno(SalaUno sala, String jugadorId) {
        Jugador jugador = sala.getJugadorById(jugadorId);
        if (jugador == null) {
            return ResultadoJugada.error("Jugador no encontrado");
        }

        if (jugador.tieneUnaCartaSola()) {
            jugador.setDijoUno(true);
            jugador.setPenalizadoUno(false);
            return ResultadoJugada.exito(jugador.getUsername(), null, jugador.getUsername() + " dijo ¡UNO!");
        } else {
            return ResultadoJugada.error("No puedes decir UNO con " + jugador.getCantidadCartas() + " cartas");
        }
    }

    public static ResultadoJugada reportarUno(SalaUno sala, String reportadorId, String reportadoId) {
        Jugador reportado = sala.getJugadorById(reportadoId);
        if (reportado == null) {
            return ResultadoJugada.error("Jugador no encontrado");
        }

        if (reportado.tieneUnaCartaSola() && !reportado.isDijoUno()) {
            Carta c1 = MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte());
            Carta c2 = MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte());
            reportado.agregarCarta(c1);
            reportado.agregarCarta(c2);
            reportado.setPenalizadoUno(true);
            return ResultadoJugada.exito(reportado.getUsername(), null,
                    reportado.getUsername() + " no dijo UNO — roba 2 cartas de penalización");
        } else {
            return ResultadoJugada.error("No se puede reportar a ese jugador");
        }
    }

    private static String aplicarEfecto(SalaUno sala, Carta carta) {
        switch (carta.getValor()) {
            case SALTA -> {
                siguienteTurno(sala);
                String saltado = sala.getJugadorActual().getUsername();
                siguienteTurno(sala);
                return saltado + " fue saltado";
            }
            case REVERSA -> {
                sala.setSentidoHorario(!sala.isSentidoHorario());
                if (sala.getJugadores().size() == 2) {
                    return "Sentido invertido — juegas de nuevo";
                } else {
                    siguienteTurno(sala);
                    return "Sentido invertido";
                }
            }
            case MAS_DOS -> {
                sala.setPenalizacionAcumulada(sala.getPenalizacionAcumulada() + 2);
                sala.setTipoPenalizacion("MAS_DOS");
                siguienteTurno(sala);
                Jugador siguiente = sala.getJugadorActual();
                siguiente.setDebeResponderPenalizacion(true);
                return siguiente.getUsername() + " recibe +" + sala.getPenalizacionAcumulada()
                        + " — puede responder con +2 o comer las cartas";
            }
            case MAS_CUATRO -> {
                sala.setPenalizacionAcumulada(sala.getPenalizacionAcumulada() + 4);
                sala.setTipoPenalizacion("MAS_CUATRO");
                siguienteTurno(sala);
                Jugador siguiente = sala.getJugadorActual();
                siguiente.setDebeResponderPenalizacion(true);
                return siguiente.getUsername() + " recibe +" + sala.getPenalizacionAcumulada()
                        + " — puede responder con +4 o comer las cartas";
            }
            default -> {
                siguienteTurno(sala);
                return "Turno siguiente";
            }
        }
    }

    private static void aplicarEfectoPrimeraCarta(SalaUno sala) {
        Carta primera = sala.getCartaActual();
        switch (primera.getValor()) {
            case SALTA -> siguienteTurno(sala);
            case REVERSA -> sala.setSentidoHorario(false);
            case MAS_DOS -> {
                Jugador primero = sala.getJugadorActual();
                for (int i = 0; i < 2; i++) {
                    primero.agregarCarta(
                            MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte()));
                }
                siguienteTurno(sala);
            }
            default -> { }
        }
    }

    public static void siguienteTurno(SalaUno sala) {
        List<Jugador> jugadores = sala.getJugadores();
        int indiceActual = -1;
        for (int i = 0; i < jugadores.size(); i++) {
            if (jugadores.get(i).getId().equals(sala.getJugadorActualId())) {
                indiceActual = i;
                break;
            }
        }

        int siguiente;
        if (sala.isSentidoHorario()) {
            siguiente = (indiceActual + 1) % jugadores.size();
        } else {
            siguiente = (indiceActual - 1 + jugadores.size()) % jugadores.size();
        }

        sala.setJugadorActualId(jugadores.get(siguiente).getId());
    }
}