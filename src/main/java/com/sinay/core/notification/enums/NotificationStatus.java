package com.sinay.core.notification.enums;

/**
 * Bildirim durumu enum'u.
 * <p>
 * Bildirimin mevcut durumunu belirler.
 */
public enum NotificationStatus {

    /**
     * Beklemede.
     * <p>
     * Bildirim gönderilmeye hazırlanıyor.
     */
    PENDING,

    /**
     * Gönderildi.
     * <p>
     * Bildirim başarıyla gönderildi.
     */
    SENT,

    /**
     * Başarısız.
     * <p>
     * Bildirim gönderilemedi, tekrar denenmeli.
     */
    FAILED,

    /**
     * İptal edildi.
     * <p>
     * Bildirim iptal edildi, gönderilmeyecek.
     */
    CANCELLED,

    /**
     * Teslim edildi.
     * <p>
     * Bildirim alıcıya ulaştı (doğrulama yapıldı).
     */
    DELIVERED,

    /**
     * Okundu.
     * <p>
     * Bildirim alıcı tarafından okundu (sadece IN_APP için).
     */
    READ
}
