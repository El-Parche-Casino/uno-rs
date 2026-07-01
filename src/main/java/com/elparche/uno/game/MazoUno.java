package com.elparche.uno.game;

import com.elparche.uno.model.Carta;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Slf4j
public class MazoUno {

    public static List<Carta> crearMazo() {
        List<Carta> mazo = new ArrayList<>();

        for (Carta.Color color : new Carta.Color[]{
                Carta.Color.ROJO,
                Carta.Color.AZUL,
                Carta.Color.VERDE,
                Carta.Color.AMARILLO}) {

            mazo.add(Carta.builder()
                    .id(UUID.randomUUID().toString())
                    .color(color)
                    .valor(Carta.Valor.CERO)
                    .build());

            for (Carta.Valor valor : new Carta.Valor[]{
                    Carta.Valor.UNO, Carta.Valor.DOS, Carta.Valor.TRES,
                    Carta.Valor.CUATRO, Carta.Valor.CINCO, Carta.Valor.SEIS,
                    Carta.Valor.SIETE, Carta.Valor.OCHO, Carta.Valor.NUEVE,
                    Carta.Valor.SALTA, Carta.Valor.REVERSA, Carta.Valor.MAS_DOS}) {
                mazo.add(Carta.builder()
                        .id(UUID.randomUUID().toString())
                        .color(color)
                        .valor(valor)
                        .build());
                mazo.add(Carta.builder()
                        .id(UUID.randomUUID().toString())
                        .color(color)
                        .valor(valor)
                        .build());
            }
        }

        for (int i = 0; i < 4; i++) {
            mazo.add(Carta.builder()
                    .id(UUID.randomUUID().toString())
                    .color(Carta.Color.COMODIN)
                    .valor(Carta.Valor.CAMBIO_COLOR)
                    .build());
        }

        for (int i = 0; i < 4; i++) {
            mazo.add(Carta.builder()
                    .id(UUID.randomUUID().toString())
                    .color(Carta.Color.COMODIN)
                    .valor(Carta.Valor.MAS_CUATRO)
                    .build());
        }

        Collections.shuffle(mazo);
        log.info("Mazo creado con {} cartas", mazo.size());
        return mazo;
    }

    public static Carta robarCarta(List<Carta> mazo, List<Carta> pillaDescarte) {
        if (mazo.isEmpty()) {
            if (pillaDescarte.size() <= 1) {
                throw new RuntimeException("No hay cartas disponibles");
            }
            Carta ultima = pillaDescarte.get(pillaDescarte.size() - 1);
            List<Carta> nuevasMazo = new ArrayList<>(
                    pillaDescarte.subList(0, pillaDescarte.size() - 1));
            Collections.shuffle(nuevasMazo);
            mazo.addAll(nuevasMazo);
            pillaDescarte.clear();
            pillaDescarte.add(ultima);
            log.info("Mazo rebarajado con {} cartas", mazo.size());
        }
        return mazo.remove(0);
    }

    public static List<Carta> repartirMano(List<Carta> mazo,
                                           List<Carta> pillaDescarte,
                                           int cantidad) {
        List<Carta> mano = new ArrayList<>();
        for (int i = 0; i < cantidad; i++) {
            mano.add(robarCarta(mazo, pillaDescarte));
        }
        return mano;
    }
}