package com.sinay.core.server.audit.listener;

import com.sinay.core.server.audit.entity.AuditLogEntity;
import com.sinay.core.server.audit.event.AuditEvent;
import com.sinay.core.server.core.ObjectCore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Audit olaylarını dinler ve loglar.
 * <p>
 * {@link AuditEvent} yayınlandığında bu listener otomatik tetiklenir.
 * Asenkron çalışarak ana işlemı bloklamaz.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditEventListener {

    /**
     * Audit olayını dinler ve log kaydı oluşturur.
     * <p>
     * Asenkron olarak çalışır, performansı etkilemez.
     *
     * @param event Audit olayı
     */
    @EventListener
    @Async
    @Transactional
    public void handleAuditEvent(AuditEvent event) {
        try {
            // AuditLogEntity oluştur
            AuditLogEntity auditLog = AuditLogEntity.builder()
                    .userId(event.getUserId())
                    .username(event.getUsername())
                    .entityClass(event.getEntityClass())
                    .entityId(event.getEntityId())
                    .action(event.getAction())
                    .logData(event.getEntityData())
                    .ipAddress(event.getIpAddress())
                    .userAgent(event.getUserAgent())
                    .executionTime(event.getTimestamp())
                    .success(true)
                    .build();

            // Log'u kaydet (AuditLogEntity.getLog() zaten false döner, loglanmaz)
            ObjectCore.save(auditLog);

            log.debug("Audit log kaydedildi: action={}, entity={}, id={}",
                    event.getAction(), event.getEntityClass(), event.getEntityId());

        } catch (Exception e) {
            // Log hatası işlemi engellemesin
            log.error("Audit log kaydedilemedi: action={}, entity={}, error={}",
                    event.getAction(), event.getEntityClass(), e.getMessage());
        }
    }
}
