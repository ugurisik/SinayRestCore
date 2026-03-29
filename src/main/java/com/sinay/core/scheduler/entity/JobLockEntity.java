package com.sinay.core.scheduler.entity;

import com.sinay.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

/**
 * Job için distributed lock entity'si.
 * <p>
 * Cluster ortamında aynı job'un sadece bir instance tarafından çalıştırılmasını sağlar.
 * <p>
 * Lock mekanizması:
 * <ol>
 *   <li>Job başlamadan önce lock alınmaya çalışılır</li>
 *   <li>Lock varsa ve süresi geçmediyse, başka instance job'u çalıştırmaz</li>
 *   <li>Job tamamlandıktan sonra lock serbest bırakılır</li>
 *   <li>Lock timeout geçerse ve hala mevcutsa, force acquire edilebilir</li>
 * </ol>
 */
@Entity
@Table(name = "job_locks", indexes = {
        @Index(name = "idx_job_name", columnList = "job_name", unique = true),
        @Index(name = "idx_locked_until", columnList = "locked_until"),
        @Index(name = "idx_visible", columnList = "visible")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(callSuper = false)
public class JobLockEntity extends BaseEntity {

    /**
     * Job adı.
     * <p>
     * Benzersiz olmalıdır.
     */
    @Column(name = "job_name", nullable = false, unique = true, length = 255)
    private String jobName;

    /**
     * Lock'u alan instance ID'si.
     * <p>
     * Genellikle server hostname veya UUID olur.
     */
    @Column(name = "instance_id", nullable = false, length = 255)
    private String instanceId;

    /**
     * Lock'un alındığı zaman.
     */
    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    /**
     * Lock'un serbest bırakılacağı zaman.
     * <p>
     * Bu zaman geçerse lock otomatik expire olur.
     */
    @Column(name = "locked_until", nullable = false)
    private LocalDateTime lockedUntil;

    /**
     * Job son çalışma zamanı.
     */
    @Column(name = "last_execution_time")
    private LocalDateTime lastExecutionTime;

    /**
     * Job son çalışma süresi (milisaniye).
     */
    @Column(name = "last_execution_duration_ms")
    private Long lastExecutionDurationMs;

    /**
     * Job son çalışma başarılı mı?
     */
    @Column(name = "last_execution_success")
    private Boolean lastExecutionSuccess;

    /**
     * Job son hata mesajı.
     */
    @Column(name = "last_error_message", columnDefinition = "TEXT")
    private String lastErrorMessage;

    /**
     * Lock aktif mi?
     * <p>
     * false ise lock serbest bırakılmıştır.
     */
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

    /**
     * Lock'un expire olup olmadığını kontrol eder.
     *
     * @return true ise lock expire olmuştur
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(lockedUntil);
    }

    /**
     * Lock'u yeniler.
     *
     * @param timeoutMinutes Yeni timeout (dakika)
     */
    public void renew(int timeoutMinutes) {
        this.lockedAt = LocalDateTime.now();
        this.lockedUntil = LocalDateTime.now().plusMinutes(timeoutMinutes);
        this.isActive = true;
    }

    /**
     * Lock'u serbest bırakır.
     */
    public void release() {
        this.isActive = false;
        this.lockedUntil = LocalDateTime.now();
    }

    /**
     * Job çalışma bilgilerini günceller.
     *
     * @param durationMs Çalışma süresi
     * @param success    Başarılı mı?
     * @param errorMessage Hata mesajı (başarısız ise)
     */
    public void updateExecutionInfo(Long durationMs, Boolean success, String errorMessage) {
        this.lastExecutionTime = LocalDateTime.now();
        this.lastExecutionDurationMs = durationMs;
        this.lastExecutionSuccess = success;
        this.lastErrorMessage = errorMessage;
    }
}
