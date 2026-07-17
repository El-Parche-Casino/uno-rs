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
public class DashboardBroadcastListener implements MessageListener {

    public static final String CANAL = "dashboard.kpis";

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String json = new String(message.getBody());
            Map<String, Object> kpis = objectMapper.readValue(json, Map.class);
            messagingTemplate.convertAndSend("/topic/dashboard/kpis", kpis);
        } catch (Exception e) {
            log.error("Error reenviando KPIs del dashboard: {}", e.getMessage());
        }
    }
}
