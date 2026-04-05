package com.sinay.core.server.base;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;


@Getter
@Setter
@MappedSuperclass
public abstract class BaseEntity {

    @Id
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * Soft delete — ObjectCore'daki "visible" alanının karşılığı.
     */
    @Column(name = "visible", nullable = false)
    private Boolean visible = true;

    /**
     * Audit log'a yazılsın mı?
     * DB'ye kaydedilmez (transient).
     * Entity bazında loglamayı kapatmak için false yapılabilir.
     */
    @Transient
    private Boolean log = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    protected void prePersist() {
        if (visible == null) visible = true;
    }
}