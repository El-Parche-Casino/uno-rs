package com.elparche.uno.config;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class WalletClient {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${wallet.url:http://localhost:8083}")
    private String walletUrl;

    private final RestTemplate restTemplate;
    private volatile String tokenServicio;
    private volatile long tokenExpiraEn;

    public WalletClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(3000);
        this.restTemplate = new RestTemplate(factory);
    }

    public Double consultarSaldo(String username) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(obtenerTokenServicio());
            ResponseEntity<Map> respuesta = restTemplate.exchange(
                    walletUrl + "/api/wallet/saldo/" + username,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    Map.class);
            Object saldo = respuesta.getBody() != null ? respuesta.getBody().get("saldo") : null;
            return saldo instanceof Number ? ((Number) saldo).doubleValue() : null;
        } catch (Exception e) {
            log.warn("No se pudo consultar el saldo de {} en wallet-service: {}", username, e.getMessage());
            return null;
        }
    }

    private String obtenerTokenServicio() {
        long ahora = System.currentTimeMillis();
        if (tokenServicio == null || ahora > tokenExpiraEn - 60_000) {
            tokenServicio = Jwts.builder()
                    .subject("uno-rs")
                    .claim("rol", "SERVICE")
                    .issuedAt(new Date(ahora))
                    .expiration(new Date(ahora + 3_600_000))
                    .signWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .compact();
            tokenExpiraEn = ahora + 3_600_000;
        }
        return tokenServicio;
    }
}
