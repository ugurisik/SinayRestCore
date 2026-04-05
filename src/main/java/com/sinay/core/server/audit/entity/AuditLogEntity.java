package com.sinay.core.server.audit.entity;

import com.sinay.core.server.audit.enums.AuditAction;
import com.sinay.core.server.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Audit log kaydı entity'si.
 * <p>
 * Sistemdeki önemli işlemlerin kaydı tutulur.
 * Herhangi bir entity'nin değişimi, silinmesi, oluşturulması gibi işlemler
 * güvenlik ve takip için loglanır.
 */
@Entity
@Table(name = "audit_logs", indexes = {
        @Index(name = "idx_audit_user", columnList = "user_id"),
        @Index(name = "idx_audit_action", columnList = "action"),
        @Index(name = "idx_audit_entity", columnList = "entity_class, entity_id"),
        @Index(name = "idx_audit_time", columnList = "execution_time"),
        @Index(name = "idx_audit_visible", columnList = "visible")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class AuditLogEntity extends BaseEntity {

    /**
     * İşlemi yapan kullanıcının ID'si.
     * <p>
     * Kullanıcı giriş yapmamışsa null olur.
     * <p>
     * String olarak saklanır çünkü UUID serialize sorunları olabilir.
     */
    @Column(name = "user_id", length = 36, nullable = true)
    private String userId;

    /**
     * Kullanıcının kullanıcı adı veya email.
     */
    @Column(name = "username", length = 255)
    private String username;

    /**
     * İşlem tipi.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 50)
    private AuditAction action;

    /**
     * Entity class'ının tam adı.
     * <p>
     * Örnek: "com.sinay.core.server.entity.User"
     */
    @Column(name = "entity_class", nullable = false, length = 255)
    private String entityClass;

    /**
     * Entity ID'si (UUID string formatında).
     */
    @Column(name = "entity_id", length = 36)
    private String entityId;

    /**
     * Serialize edilmiş entity verisi.
     * <p>
     * Entity'nin o anki durumu byte[] olarak serialize edilip saklanır.
     * Daha sonra deserialize edilerek history görüntülenebilir.
     */
    @Lob
    @Column(name = "log_data", columnDefinition = "LONGBLOB")
    private byte[] logData;

    /**
     * İşlemin yapıldığı IP adresi.
     */
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    /**
     * Client'in User Agent'ı.
     */
    @Column(name = "user_agent", length = 500)
    private String userAgent;

    /**
     * İşlemin yapıldığı zaman.
     */
    @Column(name = "execution_time", nullable = false)
    private LocalDateTime executionTime;

    /**
     * İşlem başarılı mı?
     */
    @Column(name = "success", nullable = false)
    private Boolean success;

    /**
     * Hata mesajı (başarısız ise).
     */
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    /**
     * Ek açıklama/mesaj.
     */
    @Column(name = "message", length = 500)
    private String message;

    /**
     * AuditLogEntity'nin kendisi loglanmaz.
     * <p>
     * Infinite loop'u önlemek için override.
     * BaseEntity'deki "log" field'ını aynı isimle shadow ediyoruz.
     * Böylece shouldLog() reflection kontrolü false değerini okur.
     *
     * @return Her zaman false
     */
    @Transient
    private Boolean log = false;

    @Override
    public Boolean getLog() {
        return false;
    }
}
