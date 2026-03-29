package com.sinay.core.websocket.enums;

/**
 * WebSocket mesaj tipleri.
 */
public enum MessageType {

    /**
     * Bildirim mesajı.
     */
    NOTIFICATION,

    /**
     * Chat mesajı.
     */
    CHAT,

    /**
     * Sistem mesajı.
     */
    SYSTEM,

    /**
     * Alert/Warning.
     */
    ALERT,

    /**
     * User activity (login, logout, vb.).
     */
    ACTIVITY,

    /**
     * Data update (real-time güncelleme).
     */
    DATA_UPDATE,

    /**
     * Error mesajı.
     */
    ERROR
}
