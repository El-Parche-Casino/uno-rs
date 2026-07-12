package com.elparche.uno.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalaBroadcastPublisher {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public static final String CANAL = "broadcast:uno:eventos";

    public void publicarEstado(String salaId, Object sala) {
        publicar(salaId, "estado", sala);
    }

    public void publicarMensaje(String salaId, String mensaje) {
        publicar(salaId, "mensajes", Map.of("mensaje", mensaje));
    }

    public void publicarError(String salaId, String error) {
        publicar(salaId, "errores", Map.of("error", error));
    }

    public void publicarChat(String salaId, String username, String mensaje) {
        publicar(salaId, "chat", Map.of("username", username, "mensaje", mensaje));
    }

    private void publicar(String salaId, String tipo, Object payload) {
        try {
            Map<String, Object> sobre = Map.of("salaId", salaId, "tipo", tipo, "payload", payload);
            String json = objectMapper.writeValueAsString(sobre);
            redisTemplate.convertAndSend(CANAL, json);
        } catch (JsonProcessingException e) {
            log.error("Error publicando broadcast en Redis: {}", e.getMessage());
        }
    }
}