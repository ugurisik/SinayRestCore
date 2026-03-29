package com.sinay.core.websocket.dto;

import com.sinay.core.websocket.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * WebSocket mesaj DTO'su.
 * <p>
 * Tüm WebSocket mesajları bu formatı kullanır.
 * Client ↔ Server iletişimi için standard format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessage {

    /**
     * Mesaj tipi.
     */
    private MessageType type;

    /**
     * Mesaj içeriği (JSON string veya text).
     */
    private String content;

    /**
     * Mesaj başlığı (opsiyonel).
     */
    private String title;

    /**
     * Gönderen kullanıcı ID'si.
     */
    private UUID senderId;

    /**
     * Gönderen kullanıcı adı.
     */
    private String senderName;

    /**
     * Hedef kullanıcı ID'si (opsiyonel).
     * <p>
     * Null ise tüm kullanıcılara gönderilir (broadcast).
     * Dolu ise sadece hedef kullanıcıya gönderilir.
     */
    private UUID targetUserId;

    /**
     * Ek data (opsiyonel).
     * <p>
     * JSON string olarak serialize edilmiş herhangi bir data.
     */
    private String data;

    /**
     * Mesaj zamanı.
     */
    private LocalDateTime timestamp;

    /**
     * Mesaj ID'si.
     */
    private UUID messageId;
}
