package com.elparche.uno.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalaUno {

    private String id;
    private String nombre;
    private EstadoSala estado;
    private TipoSala tipo;
    private String codigo;

    @Builder.Default
    private List<Jugador> jugadores = new ArrayList<>();

    @Builder.Default
    private List<Carta> mazo = new ArrayList<>();

    @Builder.Default
    private List<Carta> pillaDescarte = new ArrayList<>();

    private Carta cartaActual;
    private String jugadorActualId;
    private boolean sentidoHorario;
    private String colorActual;

    private int maxJugadores;
    private String creadorId;
    @Builder.Default
    private Double apuestaPorJugador = 0.0;

    @Builder.Default
    private Double pozoTotal = 0.0;

    @Builder.Default
    private LocalDateTime creadaEn = LocalDateTime.now();

    @Builder.Default
    private String tipoPenalizacion = null;

    @Builder.Default
    private int penalizacionAcumulada = 0;

    public enum EstadoSala {
        ESPERANDO, EN_JUEGO, TERMINADA
    }

    public enum TipoSala {
        PUBLICA, PRIVADA
    }

    public Jugador getJugadorActual() {
        return jugadores.stream()
                .filter(j -> j.getId().equals(jugadorActualId))
                .findFirst()
                .orElse(null);
    }

    public Jugador getJugadorById(String id) {
        return jugadores.stream()
                .filter(j -> j.getId().equals(id))
                .findFirst()
                .orElse(null);
    }

    public boolean estaLlena() {
        return jugadores.size() >= maxJugadores;
    }

    public boolean tieneMinJugadores() {
        return jugadores.size() >= 2;
    }
}