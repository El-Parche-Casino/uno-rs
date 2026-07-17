package com.elparche.uno.controller;

import com.elparche.uno.game.GestorSalas;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/uno/admin")
@RequiredArgsConstructor
public class AdminController {

    private final GestorSalas gestorSalas;

    @GetMapping("/salas")
    public ResponseEntity<List<Map<String, Object>>> salasActivas() {
        return ResponseEntity.ok(gestorSalas.listarSalasActivas());
    }
}
