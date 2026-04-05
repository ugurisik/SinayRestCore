package com.sinay.core.server.event.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Domain event için base sınıf.
 * <p>
 * Sistemdeki önemli olayları temsil eder.
 * Tüm event'ler bu sınıftan extend etmelidir.
 * <p>
 * Event örnekleri:
 * <ul>
 *   <li>UserRegisteredEvent - Kullanıcı kayıt oldu</li>
 *   <li>UserBannedEvent - Kullanıcı banlandı</li>
 *   <li>FileUploadedEvent - Dosya yüklendi</li>
 *   <li>PasswordChangedEvent - Şifre değiştirildi</li>
 * </ul>
 * <p>
 * Kullanım:
 * <pre>
 * public class UserRegisteredEvent extends DomainEvent {
 *     public UserRegisteredEvent(UUID userId, String email) {
 *         super(Map.of(
 *             "userId", userId,
 *             "email", email
 *         ));
 *     }
 * }
 * </pre>
 */
@Getter
public abstract class DomainEvent {

    /**
     * Event'in oluştuğu zaman.
     */
    private final LocalDateTime occurredAt;

    /**
     * Event tipi.
     * <p>
     * Genellikle sınıf adı (örn: "UserRegisteredEvent").
     */
    private final String eventType;

    /**
     * Event payload'u.
     * <p>
     * Event ile ilgili verileri içerir.
     * Map olarak saklanır, böylece esnektir.
     */
    private final Map<String, Object> payload;

    /**
     * Constructor - occurredAt zamanını otomatik ayarlar.
     *
     * @param eventType Event tipi
     * @param payload  Event verileri
     */
    protected DomainEvent(@NonNull String eventType, @NonNull Map<String, Object> payload) {
        this.occurredAt = LocalDateTime.now();
        this.eventType = eventType;
        this.payload = payload;
    }

    /**
     * Constructor - occurredAt zamanı manuel belirtilir.
     * <p>
     * Test senaryoları için kullanışlıdır.
     *
     * @param occurredAt Oluşum zamanı
     * @param eventType  Event tipi
     * @param payload    Event verileri
     */
    protected DomainEvent(@NonNull LocalDateTime occurredAt,
                          @NonNull String eventType,
                          @NonNull Map<String, Object> payload) {
        this.occurredAt = occurredAt;
        this.eventType = eventType;
        this.payload = payload;
    }

    /**
     * Payload'tan bir değer alır.
     *
     * @param key Anahtar
     * @return Değer veya null
     */
    public Object get(String key) {
        return payload.get(key);
    }

    /**
     * Payload'tan bir değer alır (tip güvenli).
     *
     * @param key  Anahtar
     * @param type Dönüş tipi
     * @param <T>  Generic tip
     * @return Değer veya null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = payload.get(key);
        if (value != null && type.isInstance(value)) {
            return (T) value;
        }
        return null;
    }

    @Override
    public String toString() {
        return "DomainEvent{" +
                "eventType='" + eventType + '\'' +
                ", occurredAt=" + occurredAt +
                ", payload=" + payload +
                '}';
    }
}
