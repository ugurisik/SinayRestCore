package com.sinay.core.notification.entity;

import com.sinay.core.base.BaseEntity;
import com.sinay.core.notification.enums.NotificationStatus;
import com.sinay.core.notification.enums.NotificationType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Bildirim entity'si.
 * <p>
 * Kullanıcılara gönderilen bildirimleri temsil eder.
 * Çeşitli kanallar üzerinden bildirim gönderilmesini sağlar.
 */
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notification_user", columnList = "user_id"),
        @Index(name = "idx_notification_type", columnList = "type"),
        @Index(name = "idx_notification_status", columnList = "status"),
        @Index(name = "idx_notification_scheduled", columnList = "scheduled_at"),
        @Index(name = "idx_notification_visible", columnList = "visible")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class Notification extends BaseEntity {

    /**
     * Bildirimin gönderileceği kullanıcının ID'si.
     */
    @Column(name = "user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID userId;

    /**
     * Bildirim tipi.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private NotificationType type;

    /**
     * Bildirim durumu.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationStatus status;

    /**
     * Bildirim başlığı.
     */
    @Column(name = "title", nullable = false, length = 255)
    private String title;

    /**
     * Bildirim mesajı.
     */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /**
     * Bildirim verisi (JSON formatında).
     * <p>
     * Ek veriler, template parametreleri vb.
     */
    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    /**
     * Planlanan gönderim zamanı.
     */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /**
     * Gönderim zamanı.
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * Okunma zamanı (sadece IN_APP için).
     */
    @Column(name = "read_at")
    private LocalDateTime readAt;

    /**
     * Retry sayısı.
     */
    @Column(name = "retry_count")
    private Integer retryCount;

    /**
     * Maksimum retry sayısı.
     */
    @Column(name = "max_retries")
    private Integer maxRetries;

    /**
     * Sonraki retry zamanı.
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * Hata kodu (başarısız ise).
     */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    /**
     * Hata mesajı (başarısız ise).
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Bildirim daha önce gönderildi mi?
     */
    @Column(name = "is_duplicate")
    private Boolean isDuplicate;

    /**
     * Orijinal bildirim ID'si (duplicate ise).
     */
    @Column(name = "original_notification_id")
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID originalNotificationId;

    /**
     * Bildirimi gönderilmeye hazırlar.
     */
    public void markAsPending() {
        this.status = NotificationStatus.PENDING;
        this.retryCount = 0;
        this.maxRetries = 3;
    }

    /**
     * Bildirimi gönderildi olarak işaretler.
     */
    public void markAsSent() {
        this.status = NotificationStatus.SENT;
        this.sentAt = LocalDateTime.now();
        this.nextRetryAt = null;
    }

    /**
     * Bildirimi başarısız olarak işaretler.
     *
     * @param errorCode    Hata kodu
     * @param errorMessage Hata mesajı
     */
    public void markAsFailed(String errorCode, String errorMessage) {
        this.status = NotificationStatus.FAILED;
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.retryCount++;

        // Sonraki retry zamanını hesapla (exponential backoff)
        if (this.retryCount < this.maxRetries) {
            long delayMinutes = (long) Math.pow(2, this.retryCount); // 2, 4, 8 dakika
            this.nextRetryAt = LocalDateTime.now().plusMinutes(delayMinutes);
        }
    }

    /**
     * Bildirimi okundu olarak işaretler.
     */
    public void markAsRead() {
        this.status = NotificationStatus.READ;
        this.readAt = LocalDateTime.now();
    }

    /**
     * Bildirimi iptal eder.
     */
    public void cancel() {
        this.status = NotificationStatus.CANCELLED;
    }

    /**
     * Retry yapılabilir mi?
     *
     * @return true ise retry yapılabilir
     */
    public boolean canRetry() {
        return this.status == NotificationStatus.FAILED &&
                this.retryCount < this.maxRetries &&
                this.nextRetryAt != null &&
                LocalDateTime.now().isAfter(this.nextRetryAt);
    }

    /**
     * Duplicate bildirim kontrolü için hash.
     *
     * @return Hash string
     */
    public String getDeduplicationHash() {
        return String.format("%s:%s:%s", this.userId, this.type, this.title);
    }
}
