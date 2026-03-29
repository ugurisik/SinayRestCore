package com.sinay.core.websocket.service;

import com.sinay.core.websocket.dto.WebSocketMessage;
import com.sinay.core.websocket.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WebSocket mesaj gönderme servisi.
 * <p>
 * Tüm WebSocket broadcast işlemleri bu service üzerinden yapılır.
 * <p>
 * Kullanım:
 * <pre>{@code
 * // Tüm kullanıcılara gönder (broadcast)
 * webSocketService.broadcast(
 *     MessageType.NOTIFICATION,
 *     "Sistem bakımda",
 *     null
 * );
 *
 * // Tek kullanıcıya gönder
 * webSocketService.sendToUser(
 *     userId,
 *     MessageType.CHAT,
 *     "Merhaba!",
 *     null
 * );
 * }</pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Tüm kullanıcılara mesaj gönderir (broadcast).
     *
     * @param type    Mesaj tipi
     * @param content Mesaj içeriği
     * @param title   Mesaj başlığı (opsiyonel)
     */
    public void broadcast(MessageType type, String content, String title) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID())
                .type(type)
                .content(content)
                .title(title)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/broadcast", message);

        log.debug("Broadcast gönderildi: type={}, title={}", type, title);
    }

    /**
     * Tüm kullanıcılara mesaj gönderir (detaylı).
     *
     * @param message Mesaj objesi
     */
    public void broadcast(WebSocketMessage message) {
        message.setMessageId(UUID.randomUUID());
        message.setTimestamp(LocalDateTime.now());

        messagingTemplate.convertAndSend("/topic/broadcast", message);

        log.debug("Broadcast gönderildi: type={}, senderId={}", message.getType(), message.getSenderId());
    }

    /**
     * Belirli bir kullanıcıya mesaj gönderir.
     * <p>
     * Kullanıcı online değilse mesaj kaybolur (kalıcı değil).
     *
     * @param userId  Hedef kullanıcı ID'si
     * @param type    Mesaj tipi
     * @param content Mesaj içeriği
     * @param title   Mesaj başlığı (opsiyonel)
     */
    public void sendToUser(UUID userId, MessageType type, String content, String title) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID())
                .type(type)
                .content(content)
                .title(title)
                .targetUserId(userId)
                .timestamp(LocalDateTime.now())
                .build();

        // /user/{userId}/queue/messages
        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/messages",
                message
        );

        log.debug("Kullanıcıya mesaj gönderildi: userId={}, type={}", userId, type);
    }

    /**
     * Belirli bir kullanıcıya mesaj gönderir (detaylı).
     *
     * @param userId  Hedef kullanıcı ID'si
     * @param message Mesaj objesi
     */
    public void sendToUser(UUID userId, WebSocketMessage message) {
        message.setMessageId(UUID.randomUUID());
        message.setTimestamp(LocalDateTime.now());
        message.setTargetUserId(userId);

        messagingTemplate.convertAndSendToUser(
                userId.toString(),
                "/queue/messages",
                message
        );

        log.debug("Kullanıcıya mesaj gönderildi: userId={}, type={}", userId, message.getType());
    }

    /**
     * Gönderen bilgisi ile broadcast.
     *
     * @param senderId   Gönderen ID
     * @param senderName Gönderen ismi
     * @param type       Mesaj tipi
     * @param content    Mesaj içeriği
     */
    public void broadcastWithSender(UUID senderId, String senderName, MessageType type, String content) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID())
                .type(type)
                .content(content)
                .senderId(senderId)
                .senderName(senderName)
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/broadcast", message);

        log.debug("Broadcast (with sender) gönderildi: type={}, senderName={}", type, senderName);
    }

    /**
     * Topic'e specific mesaj gönderir.
     * <p>
     * Custom topic'ler için:
     * <pre>
     * sendToTopic("/topic/notifications", message)
     * sendToTopic("/topic/updates", message)
     * </pre>
     *
     * @param topic   Topic path (/topic/ ile başlamalı)
     * @param message Mesaj
     */
    public void sendToTopic(String topic, WebSocketMessage message) {
        message.setMessageId(UUID.randomUUID());
        message.setTimestamp(LocalDateTime.now());

        messagingTemplate.convertAndSend(topic, message);

        log.debug("Topic'e mesaj gönderildi: topic={}, type={}", topic, message.getType());
    }

    /**
     * Sistem mesajı yayınlar.
     * <p>
     * Bakım, uyarı, sistem durumu vb. için kullanılır.
     *
     * @param content Mesaj içeriği
     */
    public void sendSystemMessage(String content) {
        broadcast(MessageType.SYSTEM, content, "Sistem Mesajı");
    }

    /**
     * Alert mesajı yayınlar.
     * <p>
     * Önemli bildirimler için.
     *
     * @param title   Başlık
     * @param content İçerik
     */
    public void sendAlert(String title, String content) {
        broadcast(MessageType.ALERT, content, title);
    }

    /**
     * Data update bildirimi gönderir.
     * <p>
     * Real-time data güncellemeleri için.
     *
     * @param entityType Güncellenen entity tipi
     * @param entityId   Güncellenen entity ID
     * @param action     Aksiyon (CREATE, UPDATE, DELETE)
     */
    public void sendDataUpdate(String entityType, UUID entityId, String action) {
        WebSocketMessage message = WebSocketMessage.builder()
                .messageId(UUID.randomUUID())
                .type(MessageType.DATA_UPDATE)
                .content(String.format("%s %s: %s", entityType, action, entityId))
                .title("Data Update")
                .data(String.format("{\"entityType\":\"%s\",\"entityId\":\"%s\",\"action\":\"%s\"}",
                        entityType, entityId, action))
                .timestamp(LocalDateTime.now())
                .build();

        messagingTemplate.convertAndSend("/topic/updates", message);

        log.debug("Data update gönderildi: entityType={}, entityId={}, action={}",
                entityType, entityId, action);
    }
}
