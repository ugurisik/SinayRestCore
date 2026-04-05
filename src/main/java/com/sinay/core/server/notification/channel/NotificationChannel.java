package com.sinay.core.server.notification.channel;

import com.sinay.core.server.notification.entity.Notification;
import com.sinay.core.server.notification.enums.NotificationType;

/**
 * Bildirim kanalı interface'i.
 * <p>
 * Farklı bildirim tipleri için bu interface'i implement eden sınıflar oluşturulur.
 * <p>
 * Implementasyonlar:
 * <ul>
 *   <li>EmailChannel - E-posta gönderimi</li>
 *   <li>SmsChannel - SMS gönderimi</li>
 *   <li>PushChannel - Push notification gönderimi</li>
 *   <li>InAppChannel - Uygulama içi bildirim</li>
 * </ul>
 */
public interface NotificationChannel {

    /**
     * Bildirimi gönderir.
     *
     * @param notification Gönderilecek bildirim
     * @throws Exception Gönderim hatası
     */
    void send(Notification notification) throws Exception;

    /**
     * Bu kanalın belirtilen bildirim tipini destekleyip desteklemediğini kontrol eder.
     *
     * @param type Bildirim tipi
     * @return true ise desteklenir
     */
    boolean supports(NotificationType type);

    /**
     * Kanalın mevcut durumunu kontrol eder.
     * <p>
     * Örneğin, e-posta sunucusuna bağlantı kontrolü.
     *
     * @return true ise kanal hazır
     */
    default boolean isReady() {
        return true;
    }

    /**
     * Kanalın öncelik değeri.
     * <p>
     * Düşük değer = yüksek öncelik.
     *
     * @return Öncelik değeri
     */
    default int getPriority() {
        return 100;
    }
}
