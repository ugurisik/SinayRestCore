package com.sinay.core.server.audit.event;

import com.sinay.core.server.audit.enums.AuditAction;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * Audit log olayı.
 * <p>
 * Entity üzerindeki değişiklikleri loglamak için publish edilir.
 * AuditEventListener tarafından dinlenir ve asenkron olarak loglanır.
 */
@Getter
@AllArgsConstructor
public class AuditEvent {

    /**
     * İşlemi yapan kullanıcının ID'si.
     * <p>
     * String olarak tutulur çünkü veritabanında String saklanır.
     */
    private final String userId;

    /**
     * Kullanıcının kullanıcı adı veya email'i.
     */
    private final String username;

    /**
     * Loglanan entity'nin class adı.
     * <p>
     * Örnek: "com.sinay.core.server.entity.User"
     */
    private final String entityClass;

    /**
     * Loglanan entity'nin ID'si.
     */
    private final String entityId;

    /**
     * İşlem tipi.
     */
    private final AuditAction action;

    /**
     * Serialize edilmiş entity verisi.
     * <p>
     * Entity'nin o anki durumu byte[] olarak serialize edilir.
     */
    private final byte[] entityData;

    /**
     * İşlemin yapıldığı IP adresi.
     */
    private final String ipAddress;

    /**
     * Client'in User Agent'ı.
     */
    private final String userAgent;

    /**
     * Olayın oluştuğu zaman.
     */
    private final LocalDateTime timestamp;
}
