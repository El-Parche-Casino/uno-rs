package com.elparche.uno.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class SalaBroadcastListener implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            Map<String, Object> sobre = objectMapper.readValue(json, Map.class);
            String salaId = (String) sobre.get("salaId");
            String tipo = (String) sobre.get("tipo");
            Object payload = sobre.get("payload");

            messagingTemplate.convertAndSend("/topic/uno/" + salaId + "/" + tipo, payload);
        } catch (Exception e) {
            log.error("Error procesando broadcast recibido de Redis: {}", e.getMessage());
        }
    }
}