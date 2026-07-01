package com.elparche.uno.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisEventPublisher {

    private final RedisTemplate<String, String> redisTemplate;

    public void publicarApuesta(String username, Double monto, String salaId) {
        try {
            redisTemplate.opsForStream().add(
                    "wallet.transacciones",
                    Map.of(
                            "tipo", "APUESTA",
                            "username", username,
                            "monto", String.valueOf(monto),
                            "salaId", salaId,
                            "juegoTipo", "UNO"
                    )
            );
            log.info("Apuesta publicada — username: {}, monto: {}, sala: {}",
                    username, monto, salaId);
        } catch (Exception e) {
            log.error("Error publicando apuesta: {}", e.getMessage());
        }
    }

    public void publicarGanancia(String username, Double monto, String salaId) {
        try {
            redisTemplate.opsForStream().add(
                    "wallet.transacciones",
                    Map.of(
                            "tipo", "GANANCIA",
                            "username", username,
                            "monto", String.valueOf(monto),
                            "salaId", salaId,
                            "juegoTipo", "UNO"
                    )
            );
            log.info("Ganancia publicada — username: {}, monto: {}, sala: {}",
                    username, monto, salaId);
        } catch (Exception e) {
            log.error("Error publicando ganancia: {}", e.getMessage());
        }
    }

    public void publicarDevolucion(String username, Double monto, String salaId) {
        try {
            redisTemplate.opsForStream().add(
                    "wallet.transacciones",
                    Map.of(
                            "tipo", "DEVOLUCION",
                            "username", username,
                            "monto", String.valueOf(monto),
                            "salaId", salaId,
                            "juegoTipo", "UNO"
                    )
            );
            log.info("Devolución publicada — username: {}, monto: {}, sala: {}",
                    username, monto, salaId);
        } catch (Exception e) {
            log.error("Error publicando devolución: {}", e.getMessage());
        }
    }
}