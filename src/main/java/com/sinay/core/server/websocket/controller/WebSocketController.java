package com.sinay.core.server.websocket.controller;

import com.sinay.core.server.websocket.dto.WebSocketMessage;
import com.sinay.core.server.websocket.enums.MessageType;
import com.sinay.core.server.websocket.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

/**
 * WebSocket message controller.
 * <p>
 * Client'tan gelen mesajları işler ve dağıtır.
 * <p>
 * Client → Server: /app/message (buraya düşer)
 * Server → Client: /topic/broadcast (tüm abonelere)
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final WebSocketService webSocketService;

    /**
     * Client'tan gelen mesajı alıp tüm kullanıcılara broadcast eder.
     * <p>
     * Client: SEND → /app/message { "content": "Merhaba!", "type": "CHAT" }
     * Server: RECEIVED → @MessageMapping("/message")
     * Server: BROADCAST → /topic/broadcast { ... }
     * Client: RECEIVE ← /topic/broadcast
     *
     * @param message Mesaj
     * @param accessor WebSocket session accessor
     * @param principal Kullanıcı (JWT interceptor'dan set edilir)
     */
    @MessageMapping("/message")
    @SendTo("/topic/broadcast")
    public WebSocketMessage handleMessage(
            @Payload WebSocketMessage message,
            SimpMessageHeaderAccessor accessor,
            Principal principal) {

        log.info("WebSocket mesaj alındı: from={}, type={}, content={}",
                principal != null ? principal.getName() : "anonymous",
                message.getType(),
                message.getContent());

        // Principal'dan kullanıcı bilgisini al
        if (principal != null) {
            // JWT interceptor'da set edilen user bilgisi
            String username = principal.getName();
            // User ID header'dan
            Object userIdObj = accessor.getSessionAttributes().get("userId");

            if (userIdObj != null) {
                message.setSenderId(UUID.fromString(userIdObj.toString()));
            }
            message.setSenderName(username);
        }

        // Timestamp ve ID set et
        if (message.getMessageId() == null) {
            message.setMessageId(UUID.randomUUID());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(java.time.LocalDateTime.now());
        }

        log.debug("Mesaj broadcast ediliyor: type={}, senderId={}", message.getType(), message.getSenderId());

        return message;
    }

    /**
     * Chat mesajı için özel endpoint.
     * <p>
     * Client: SEND → /app/chat { "content": "Selam!" }
     * Server: BROADCAST → /topic/chat
     */
    @MessageMapping("/chat")
    @SendTo("/topic/chat")
    public WebSocketMessage handleChat(@Payload WebSocketMessage message, Principal principal) {
        message.setType(MessageType.CHAT);

        if (principal != null) {
            message.setSenderName(principal.getName());
        }

        log.info("Chat mesajı: from={}, content={}", principal != null ? principal.getName() : "anonymous", message.getContent());

        return message;
    }

    /**
     * Aktivite mesajı (kullanıcı giriş/çıkış, vb.).
     */
    @MessageMapping("/activity")
    @SendTo("/topic/activity")
    public WebSocketMessage handleActivity(@Payload WebSocketMessage message, Principal principal) {
        message.setType(MessageType.ACTIVITY);

        if (principal != null) {
            message.setSenderName(principal.getName());
        }

        log.info("Aktivite mesajı: from={}, content={}", principal != null ? principal.getName() : "anonymous", message.getContent());

        return message;
    }
}
