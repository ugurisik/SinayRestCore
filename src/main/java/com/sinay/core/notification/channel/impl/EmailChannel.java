package com.sinay.core.notification.channel.impl;

import com.sinay.core.notification.channel.NotificationChannel;
import com.sinay.core.notification.entity.Notification;
import com.sinay.core.notification.enums.NotificationType;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * E-posta bildirim kanalı implementasyonu.
 * <p>
 * Bildirimleri e-posta olarak gönderir.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>HTML e-posta desteği</li>
 *   <li>Ek dosya desteği</li>
 *   <li>Asenkron gönderim</li>
 *   <li>Hata yönetimi</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmailChannel implements NotificationChannel {

    private final JavaMailSender mailSender;

    /**
     * Varsayılan gönderen e-posta adresi.
     */
    private static final String DEFAULT_FROM = "noreply@sinay.com";

    /**
     * E-posta gönderir.
     * <p>
     * Bu metot asenkron olarak çalışır.
     *
     * @param notification Gönderilecek bildirim
     * @throws Exception Gönderim hatası
     */
    @Async
    @Override
    public void send(Notification notification) throws Exception {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            // Gönderen
            helper.setFrom(DEFAULT_FROM);
            helper.setReplyTo(DEFAULT_FROM);

            // Alıcı (payload'dan email'i al)
            String toEmail = extractEmail(notification);
            helper.setTo(toEmail);

            // Başlık ve mesaj
            helper.setSubject(notification.getTitle());
            helper.setText(notification.getMessage(), true); // true = HTML

            // E-posta gönder
            mailSender.send(mimeMessage);

            log.info("Email sent: to={}, title={}", toEmail, notification.getTitle());

        } catch (Exception e) {
            log.error("Failed to send email: title={}, error={}", notification.getTitle(), e.getMessage());
            throw e;
        }
    }

    /**
     * EMAIL tipini destekler.
     *
     * @param type Bildirim tipi
     * @return true ise EMAIL tipi
     */
    @Override
    public boolean supports(NotificationType type) {
        return NotificationType.EMAIL.equals(type);
    }

    /**
     * Kanal hazır mı kontrol eder.
     *
     * @return true ise JavaMailSender yapılandırılmış
     */
    @Override
    public boolean isReady() {
        return mailSender != null;
    }

    /**
     * Öncelik değeri.
     * <p>
     * E-posta orta öncelikli.
     *
     * @return Öncelik değeri
     */
    @Override
    public int getPriority() {
        return 50;
    }

    /**
     * Bildirimden e-posta adresini çıkarır.
     *
     * @param notification Bildirim
     * @return E-posta adresi
     */
    private String extractEmail(Notification notification) {
        // Payload'dan email'i almaya çalış
        if (notification.getPayload() != null) {
            try {
                // JSON parse et (basit implementasyon)
                if (notification.getPayload().contains("\"email\"")) {
                    int start = notification.getPayload().indexOf("\"email\":\"") + 9;
                    int end = notification.getPayload().indexOf("\"", start);
                    if (start > 9 && end > start) {
                        return notification.getPayload().substring(start, end);
                    }
                }
            } catch (Exception e) {
                log.trace("Failed to extract email from payload: {}", e.getMessage());
            }
        }

        // Varsayılan: kullanıcı ID'si @domain.com formatında
        // Gerçek uygulamada kullanıcı entity'sinden email alınmalı
        return notification.getUserId() + "@example.com";
    }
}
