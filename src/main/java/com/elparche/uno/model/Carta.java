package com.elparche.uno.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Carta {

    private String id;
    private Color color;
    private Valor valor;

    public enum Color {
        ROJO, AZUL, VERDE, AMARILLO, COMODIN
    }

    public enum Valor {
        CERO, UNO, DOS, TRES, CUATRO, CINCO, SEIS, SIETE, OCHO, NUEVE,
        SALTA, REVERSA, MAS_DOS, MAS_CUATRO, CAMBIO_COLOR
    }

    public boolean esComodin() {
        return color == Color.COMODIN;
    }

    public boolean esCompatibleCon(Carta cartaEnMesa) {
        if (this.esComodin()) return true;
        return this.color == cartaEnMesa.color ||
                this.valor == cartaEnMesa.valor;
    }

    public String getNombre() {
        if (esComodin()) return valor.name();
        return color.name() + "_" + valor.name();
    }
}