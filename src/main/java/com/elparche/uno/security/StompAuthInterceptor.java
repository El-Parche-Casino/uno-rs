package com.elparche.uno.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                log.warn("Conexion WebSocket rechazada: falta header Authorization");
                throw new org.springframework.messaging.MessagingException(
                        "Falta el token de autenticacion");
            }

            String jwt = authHeader.substring(7);

            try {
                String username = jwtUtil.extractUsername(jwt);
                if (username == null || jwtUtil.isTokenExpired(jwt)) {
                    log.warn("Conexion WebSocket rechazada: token invalido o expirado");
                    throw new org.springframework.messaging.MessagingException(
                            "Token invalido o expirado");
                }
                accessor.setUser(() -> username);
                if (accessor.getSessionAttributes() != null) {
                    accessor.getSessionAttributes().put("rol", jwtUtil.extractRol(jwt));
                }
                log.info("Conexion WebSocket autenticada para usuario: {}", username);
            } catch (org.springframework.messaging.MessagingException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Conexion WebSocket rechazada: token no se pudo validar - {}", e.getMessage());
                throw new org.springframework.messaging.MessagingException(
                        "Token invalido");
            }
        }

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destino = accessor.getDestination();
            if (destino != null && destino.startsWith("/topic/dashboard/")) {
                Object rol = accessor.getSessionAttributes() != null
                        ? accessor.getSessionAttributes().get("rol")
                        : null;
                if (!"ADMIN".equals(rol)) {
                    log.warn("Suscripcion al dashboard rechazada para {} (rol: {})",
                            accessor.getUser() != null ? accessor.getUser().getName() : "desconocido", rol);
                    throw new org.springframework.messaging.MessagingException(
                            "Solo un ADMIN puede suscribirse al dashboard");
                }
            }
        }

        return message;
    }
}