package com.elparche.uno.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class ContadorSesiones {

    private final RedisTemplate<String, String> redisTemplate;
    private final AtomicInteger sesiones = new AtomicInteger(0);

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        sesiones.incrementAndGet();
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        sesiones.updateAndGet(actual -> Math.max(0, actual - 1));
    }

    @Scheduled(fixedRate = 5000)
    public void publicarConteo() {
        try {
            String clave = "dashboard:sesiones:uno:" + nombreHost();
            redisTemplate.opsForValue().set(clave,
                    String.valueOf(sesiones.get()), Duration.ofSeconds(15));
            redisTemplate.opsForValue().set("dashboard:salas:uno",
                    String.valueOf(contarSalasActivas()), Duration.ofSeconds(15));
        } catch (Exception e) {
            log.warn("No se pudo publicar el conteo de sesiones: {}", e.getMessage());
        }
    }

    private long contarSalasActivas() {
        var claves = redisTemplate.keys("sala:uno:*");
        if (claves == null) return 0;
        long activas = 0;
        for (String clave : claves) {
            String json = redisTemplate.opsForValue().get(clave);
            if (json != null && !json.contains("\"estado\":\"TERMINADA\"")) {
                activas++;
            }
        }
        return activas;
    }

    private String nombreHost() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "desconocido";
        }
    }
}
