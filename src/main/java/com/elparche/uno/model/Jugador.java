package com.elparche.uno.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Jugador {

    private String id;
    private String username;
    private boolean conectado;

    @Builder.Default
    private List<Carta> mano = new ArrayList<>();

    @Builder.Default
    private boolean dijoUno = false;

    @Builder.Default
    private int cartasRobadas = 0;

    public int getCantidadCartas() {
        return mano.size();
    }

    public boolean tieneUnaCartaSola() {
        return mano.size() == 1;
    }

    public boolean gano() {
        return mano.isEmpty();
    }

    public void agregarCarta(Carta carta) {
        mano.add(carta);
    }

    public boolean quitarCarta(Carta carta) {
        return mano.removeIf(c -> c.getId().equals(carta.getId()));
    }
}