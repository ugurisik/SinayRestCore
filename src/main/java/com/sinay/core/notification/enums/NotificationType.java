package com.sinay.core.notification.enums;

/**
 * Bildirim tipi enum'u.
 * <p>
 * Bildirimlerin hangi kanal üzerinden gönderileceğini belirler.
 */
public enum NotificationType {

    /**
     * E-posta bildirimi.
     * <p>
     * SMTP üzerinden e-posta gönderilir.
     */
    EMAIL,

    /**
     * SMS bildirimi.
     * <p>
     * SMS gateway üzerinden SMS gönderilir.
     */
    SMS,

    /**
     * Push bildirimi.
     * <p>
     * Firebase Cloud Messaging (FCM) veya OneSignal üzerinden push notification gönderilir.
     */
    PUSH,

    /**
     * Uygulama içi bildirim.
     * <p>
     * Bildirim veritabanında saklanır, kullanıcı uygulamada görüntüler.
     */
    IN_APP
}
