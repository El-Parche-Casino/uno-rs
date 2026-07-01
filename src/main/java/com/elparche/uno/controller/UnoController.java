package com.elparche.uno.controller;

import com.elparche.uno.game.GestorSalas;
import com.elparche.uno.model.SalaUno;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

@RestController
@RequestMapping("/api/uno")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
@Tag(name = "UNO", description = "Endpoints REST para gestión de salas del juego UNO")
public class UnoController {

    private final GestorSalas gestorSalas;

    @Operation(summary = "Crear sala", description = "Crea una sala pública o privada de UNO con apuesta por jugador")
    @PostMapping("/salas")
    public ResponseEntity<SalaUno> crearSala(@RequestBody Map<String, String> body) {
        try {
            SalaUno sala = gestorSalas.crearSala(
                    body.get("nombre"),
                    body.get("creadorId"),
                    body.get("creadorUsername"),
                    Integer.parseInt(body.getOrDefault("maxJugadores", "4")),
                    SalaUno.TipoSala.valueOf(body.getOrDefault("tipo", "PUBLICA")),
                    Double.parseDouble(body.getOrDefault("apuestaPorJugador", "50.0"))
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(sala);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Unirse a sala", description = "Un jugador se une a una sala existente y su apuesta se descuenta automáticamente")
    @PostMapping("/salas/{salaId}/unirse")
    public ResponseEntity<SalaUno> unirseASala(
            @PathVariable String salaId,
            @RequestBody Map<String, String> body) {
        try {
            SalaUno sala = gestorSalas.unirseASala(
                    salaId,
                    body.get("jugadorId"),
                    body.get("jugadorUsername"),
                    body.get("codigo")
            );
            return ResponseEntity.ok(sala);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Ver salas públicas", description = "Lista todas las salas públicas disponibles con su apuesta")
    @GetMapping("/salas/publicas")
    public ResponseEntity<Collection<SalaUno>> getSalasPublicas() {
        return ResponseEntity.ok(gestorSalas.getSalasPublicas());
    }

    @Operation(summary = "Ver sala", description = "Obtiene el estado actual de una sala incluyendo el pozo acumulado")
    @GetMapping("/salas/{salaId}")
    public ResponseEntity<SalaUno> getSala(@PathVariable String salaId) {
        SalaUno sala = gestorSalas.getSala(salaId);
        if (sala == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(sala);
    }

    @Operation(summary = "Health check", description = "Verifica que el uno-rs esté corriendo")
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("UNO Room Server funcionando correctamente");
    }
}