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

        Carta cartaActual = sala.getCartaActual();
        Carta cartaEfectiva = Carta.builder()
                .color(Carta.Color.valueOf(sala.getColorActual()))
                .valor(cartaActual.getValor())
                .id(cartaActual.getId())
                .build();

        if (!carta.esCompatibleCon(cartaEfectiva)) {
            return ResultadoJugada.error("Esa carta no es compatible con la carta actual");
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

        if (jugador.gano()) {
            sala.setEstado(SalaUno.EstadoSala.TERMINADA);
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
        Carta carta = MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte());
        jugador.agregarCarta(carta);

        siguienteTurno(sala);

        return ResultadoJugada.robo(jugador.getUsername(), carta);
    }

    public static ResultadoJugada decirUno(SalaUno sala, String jugadorId) {
        Jugador jugador = sala.getJugadorById(jugadorId);
        if (jugador == null) {
            return ResultadoJugada.error("Jugador no encontrado");
        }

        if (jugador.tieneUnaCartaSola()) {
            jugador.setDijoUno(true);
            return ResultadoJugada.exito(jugador.getUsername(), null, "¡UNO!");
        } else {

            Carta c1 = MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte());
            Carta c2 = MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte());
            jugador.agregarCarta(c1);
            jugador.agregarCarta(c2);
            return ResultadoJugada.error(
                    jugador.getUsername() + " dijo UNO sin tener una carta — roba 2");
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
                    siguienteTurno(sala);
                } else {
                    siguienteTurno(sala);
                }
                return "Sentido invertido";
            }
            case MAS_DOS -> {
                siguienteTurno(sala);
                Jugador siguiente = sala.getJugadorActual();
                for (int i = 0; i < 2; i++) {
                    siguiente.agregarCarta(
                            MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte()));
                }
                siguienteTurno(sala);
                return siguiente.getUsername() + " roba 2 cartas y pierde turno";
            }
            case MAS_CUATRO -> {
                siguienteTurno(sala);
                Jugador siguiente = sala.getJugadorActual();
                for (int i = 0; i < 4; i++) {
                    siguiente.agregarCarta(
                            MazoUno.robarCarta(sala.getMazo(), sala.getPillaDescarte()));
                }
                siguienteTurno(sala);
                return siguiente.getUsername() + " roba 4 cartas y pierde turno";
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