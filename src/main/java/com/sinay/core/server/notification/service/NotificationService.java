package com.sinay.core.server.notification.service;

import com.sinay.core.server.core.ObjectCore;
import com.sinay.core.server.notification.channel.NotificationChannel;
import com.sinay.core.server.notification.entity.Notification;
import com.sinay.core.server.notification.entity.QNotification;
import com.sinay.core.server.notification.enums.NotificationStatus;
import com.sinay.core.server.notification.enums.NotificationType;
import com.querydsl.core.types.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bildirim servisi.
 * <p>
 * Tüm bildirim işlemlerini yönetir.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>Çoklu kanal desteği (Email, SMS, Push, In-App)</li>
 *   <li>Asenkron gönderim</li>
 *   <li>Retry mekanizması</li>
 *   <li>Duplicate kontrolü</li>
 *   <li>Zamanlanmış gönderim</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final List<NotificationChannel> channels;
    private final Map<NotificationType, NotificationChannel> channelCache = new ConcurrentHashMap<>();

    /**
     * Yeni bir bildirim oluşturur ve kuyruğa ekler.
     *
     * @param userId  Kullanıcı ID'si
     * @param type    Bildirim tipi
     * @param title   Başlık
     * @param message Mesaj
     * @return Oluşturulan bildirim
     */
    @Transactional
    public Notification createNotification(UUID userId, NotificationType type, String title, String message) {
        return createNotification(userId, type, title, message, null);
    }

    /**
     * Yeni bir bildirim oluşturur ve kuyruğa ekler (payload ile).
     *
     * @param userId  Kullanıcı ID'si
     * @param type    Bildirim tipi
     * @param title   Başlık
     * @param message Mesaj
     * @param payload Ek veriler (JSON)
     * @return Oluşturulan bildirim
     */
    @Transactional
    public Notification createNotification(UUID userId, NotificationType type, String title, String message, String payload) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .payload(payload)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .isDuplicate(false)
                .build();

        notification.markAsPending();

        ObjectCore.Result<Notification> result = ObjectCore.save(notification);
        Notification saved = result.getData();
        log.debug("Notification created: id={}, type={}, user={}", saved.getId(), type, userId);

        return saved;
    }

    /**
     * Zamanlanmış bildirim oluşturur.
     *
     * @param userId       Kullanıcı ID'si
     * @param type         Bildirim tipi
     * @param title        Başlık
     * @param message      Mesaj
     * @param scheduledAt Gönderim zamanı
     * @return Oluşturulan bildirim
     */
    @Transactional
    public Notification createScheduledNotification(UUID userId, NotificationType type, String title,
                                                   String message, LocalDateTime scheduledAt) {
        Notification notification = Notification.builder()
                .userId(userId)
                .type(type)
                .title(title)
                .message(message)
                .scheduledAt(scheduledAt)
                .status(NotificationStatus.PENDING)
                .retryCount(0)
                .maxRetries(3)
                .isDuplicate(false)
                .build();

        notification.markAsPending();

        ObjectCore.Result<Notification> result = ObjectCore.save(notification);
        Notification saved = result.getData();
        log.debug("Scheduled notification created: id={}, type={}, user={}, scheduled_at={}",
                saved.getId(), type, userId, scheduledAt);

        return saved;
    }

    /**
     * Bekleyen bildirimleri gönderir.
     * <p>
     * Bu metot scheduled job tarafından düzenli olarak çağrılabilir.
     */
    @Async
    @Transactional
    public void sendPendingNotifications() {
        try {
            QNotification q = QNotification.notification;
            Predicate predicate = q.status.eq(NotificationStatus.PENDING)
                    .and(q.scheduledAt.isNull().or(q.scheduledAt.before(LocalDateTime.now())))
                    .and(q.visible.isTrue());

            ObjectCore.ListResult<Notification> result = ObjectCore.list(q, predicate, null);
            List<Notification> pending = result.getData();

            log.debug("Found {} pending notifications to send", pending.size());

            for (Notification notification : pending) {
                sendNotification(notification);
            }
        } catch (Exception e) {
            log.error("Failed to send pending notifications: error={}", e.getMessage());
        }
    }

    /**
     * Retry edilebilir bildirimleri tekrar gönderir.
     * <p>
     * Bu metot scheduled job tarafından düzenli olarak çağrılabilir.
     */
    @Async
    @Transactional
    public void retryFailedNotifications() {
        try {
            QNotification q = QNotification.notification;
            Predicate predicate = q.status.eq(NotificationStatus.FAILED)
                    .and(q.retryCount.lt(q.maxRetries))
                    .and(q.nextRetryAt.isNotNull().and(q.nextRetryAt.before(LocalDateTime.now())))
                    .and(q.visible.isTrue());

            ObjectCore.ListResult<Notification> result = ObjectCore.list(q, predicate, null);
            List<Notification> retryable = result.getData();

            log.debug("Found {} retryable notifications", retryable.size());

            for (Notification notification : retryable) {
                sendNotification(notification);
            }
        } catch (Exception e) {
            log.error("Failed to retry notifications: error={}", e.getMessage());
        }
    }

    /**
     * Bildirimi gönderir.
     *
     * @param notification Gönderilecek bildirim
     */
    @Async
    @Transactional
    public void sendNotification(Notification notification) {
        try {
            // Uygun kanalı bul
            NotificationChannel channel = getChannelForType(notification.getType());

            if (channel == null) {
                log.warn("No channel found for notification type: {}", notification.getType());
                notification.markAsFailed("NO_CHANNEL", "No channel found for type: " + notification.getType());
                ObjectCore.save(notification);
                return;
            }

            // Kanal hazır mı?
            if (!channel.isReady()) {
                log.warn("Channel not ready: type={}", notification.getType());
                notification.markAsFailed("CHANNEL_NOT_READY", "Channel not ready");
                ObjectCore.save(notification);
                return;
            }

            // Bildirimi gönder
            channel.send(notification);

            // Başarılı
            notification.markAsSent();
            ObjectCore.save(notification);

            log.info("Notification sent: id={}, type={}, user={}",
                    notification.getId(), notification.getType(), notification.getUserId());

        } catch (Exception e) {
            log.error("Failed to send notification: id={}, type={}, error={}",
                    notification.getId(), notification.getType(), e.getMessage());

            // Başarısız
            notification.markAsFailed("SEND_FAILED", e.getMessage());

            // Tekrar deneme şansı varsa PENDING'e al
            if (notification.canRetry()) {
                notification.setStatus(NotificationStatus.PENDING);
            }

            ObjectCore.save(notification);
        }
    }

    /**
     * Kullanıcıya ait bildirimleri döndürür.
     *
     * @param userId Kullanıcı ID'si
     * @return Bildirim listesi
     */
    @Transactional(readOnly = true)
    public List<Notification> getUserNotifications(UUID userId) {
        QNotification q = QNotification.notification;
        Predicate predicate = q.userId.eq(userId).and(q.visible.isTrue());
        ObjectCore.ListResult<Notification> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }

    /**
     * Kullanıcıya ait okunmamış bildirimleri döndürür.
     *
     * @param userId Kullanıcı ID'si
     * @return Okunmamış bildirim listesi
     */
    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotifications(UUID userId) {
        QNotification q = QNotification.notification;
        Predicate predicate = q.userId.eq(userId)
                .and(q.status.eq(NotificationStatus.SENT))
                .and(q.visible.isTrue());
        ObjectCore.ListResult<Notification> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }

    /**
     * Bildirimi okundu olarak işaretler.
     *
     * @param notificationId Bildirim ID'si
     */
    @Transactional
    public void markAsRead(UUID notificationId) {
        ObjectCore.Result<Notification> result = ObjectCore.getById(Notification.class, notificationId);
        if (result.isSuccess()) {
            Notification notification = result.getData();
            notification.markAsRead();
            ObjectCore.save(notification);
            log.debug("Notification marked as read: id={}", notificationId);
        }
    }

    /**
     * Kullanıcının tüm bildirimlerini okundu olarak işaretler.
     *
     * @param userId Kullanıcı ID'si
     */
    @Transactional
    public void markAllAsRead(UUID userId) {
        List<Notification> unread = getUnreadNotifications(userId);
        for (Notification notification : unread) {
            notification.markAsRead();
            ObjectCore.save(notification);
        }
        log.info("Marked {} notifications as read for user: {}", unread.size(), userId);
    }

    /**
     * Bildirim tipi için kanal döndürür.
     *
     * @param type Bildirim tipi
     * @return Kanal veya null
     */
    private NotificationChannel getChannelForType(NotificationType type) {
        // Cache'ten al
        if (channelCache.containsKey(type)) {
            return channelCache.get(type);
        }

        // Uygun kanalı bul
        NotificationChannel channel = channels.stream()
                .filter(ch -> ch.supports(type))
                .filter(NotificationChannel::isReady)
                .min((ch1, ch2) -> Integer.compare(ch1.getPriority(), ch2.getPriority()))
                .orElse(null);

        if (channel != null) {
            channelCache.put(type, channel);
        }

        return channel;
    }

    /**
     * Okunmamış bildirim sayısını döndürür.
     *
     * @param userId Kullanıcı ID'si
     * @return Okunmamış bildirim sayısı
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID userId) {
        QNotification q = QNotification.notification;
        Predicate predicate = q.userId.eq(userId)
                .and(q.status.eq(NotificationStatus.SENT))
                .and(q.visible.isTrue());
        return ObjectCore.count(q, predicate);
    }

    /**
     * Eski bildirimleri temizler.
     * <p>
     * Bu metot scheduled job tarafından düzenli olarak çağrılabilir.
     *
     * @param days Gün sayısı (bu tarihten önceki bildirimler silinir)
     */
    @Transactional
    public void cleanOldNotifications(int days) {
        LocalDateTime beforeDate = LocalDateTime.now().minusDays(days);
        QNotification q = QNotification.notification;
        Predicate predicate = q.createdAt.before(beforeDate).and(q.visible.isTrue());

        ObjectCore.ListResult<Notification> result = ObjectCore.list(q, predicate, null);
        List<Notification> oldNotifications = result.getData();

        for (Notification notification : oldNotifications) {
            ObjectCore.delete(notification);
        }
        log.info("Cleaned {} old notifications (older than {} days)", oldNotifications.size(), days);
    }
}
