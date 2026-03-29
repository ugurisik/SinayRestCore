package com.sinay.core.audit.service;

import com.sinay.core.audit.entity.AuditLogEntity;
import com.sinay.core.audit.entity.QAuditLogEntity;
import com.sinay.core.audit.enums.AuditAction;
import com.sinay.core.core.ObjectCore;
import com.querydsl.core.types.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Audit log servisi.
 * <p>
 * Sistemdeki önemli işlemlerin kaydını tutar.
 * Asenkron olarak çalışır, böylece ana işlemi yavaşlatmaz.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    /**
     * Audit log kaydı oluşturur.
     * <p>
     * Bu metot asenkron olarak çalışır.
     *
     * @param userId       Kullanıcı ID'si
     * @param username     Kullanıcı adı
     * @param action       İşlem tipi
     * @param entityClass  Entity class adı
     * @param entityId     Entity ID'si
     * @param logData      Serialize edilmiş entity verisi
     * @param ipAddress    IP adresi
     * @param userAgent    User Agent
     * @param success      İşlem başarılı mı?
     * @param errorMessage Hata mesajı (başarısız ise)
     * @param message      Ek mesaj
     */
    @Async
    @Transactional
    public void createLog(
            String userId,
            String username,
            AuditAction action,
            String entityClass,
            String entityId,
            byte[] logData,
            String ipAddress,
            String userAgent,
            boolean success,
            String errorMessage,
            String message
    ) {
        try {
            AuditLogEntity log_ = AuditLogEntity.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .entityClass(entityClass)
                    .entityId(entityId)
                    .logData(logData)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .executionTime(LocalDateTime.now())
                    .success(success)
                    .errorMessage(errorMessage)
                    .message(message)
                    .build();

            // AuditLogEntity'nin getLog() false döner, loglanmaz
            ObjectCore.save(log_);

            log.debug("Audit log created: action={}, entity={}, entity_id={}",
                    action, entityClass, entityId);
        } catch (Exception e) {
            log.error("Failed to create audit log: action={}, entity={}, error={}",
                    action, entityClass, e.getMessage());
        }
    }

    /**
     * Audit log kaydı oluşturur (builder ile).
     * <p>
     * Bu metot asenkron olarak çalışır.
     *
     * @param logBuilder Audit log builder
     */
    @Async
    @Transactional
    public void createLog(AuditLogEntity.AuditLogEntityBuilder logBuilder) {
        try {
            AuditLogEntity log_ = logBuilder
                    .executionTime(LocalDateTime.now())
                    .build();

            // AuditLogEntity'nin getLog() false döner, loglanmaz
            ObjectCore.save(log_);
            log.debug("Audit log created: action={}, entity={}, entity_id={}",
                    log_.getAction(), log_.getEntityClass(), log_.getEntityId());
        } catch (Exception e) {
            log.error("Failed to create audit log: error={}", e.getMessage());
        }
    }

    /**
     * Belirli bir tarihten eski log'ları temizler.
     * <p>
     * Bu metot scheduled job ile düzenli olarak çağrılabilir.
     *
     * @param beforeDate Bu tarihten önceki log'lar silinir
     * @return Silinen log sayısı
     */
    @Transactional
    public int cleanOldLogs(LocalDateTime beforeDate) {
        try {
            QAuditLogEntity q = QAuditLogEntity.auditLogEntity;
            Predicate predicate = q.executionTime.before(beforeDate).and(q.visible.isTrue());

            ObjectCore.ListResult<AuditLogEntity> result = ObjectCore.list(q, predicate, null);

            List<AuditLogEntity> oldLogs = result.getData();
            for (AuditLogEntity log : oldLogs) {
                ObjectCore.delete(log);
            }

            log.info("Cleaned {} old audit logs (before {})", oldLogs.size(), beforeDate);

            return oldLogs.size();
        } catch (Exception e) {
            log.error("Failed to clean old audit logs: error={}", e.getMessage());
            return 0;
        }
    }

    /**
     * Belirli bir kullanıcıya ait log'ları döndürür.
     *
     * @param userId Kullanıcı ID'si
     * @return Log listesi
     */
    @Transactional(readOnly = true)
    public List<AuditLogEntity> getUserLogs(String userId) {
        QAuditLogEntity q = QAuditLogEntity.auditLogEntity;
        Predicate predicate = q.userId.eq(userId).and(q.visible.isTrue());
        ObjectCore.ListResult<AuditLogEntity> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }

    /**
     * Belirli bir entity'e ait log'ları döndürür.
     *
     * @param entityClass Entity class adı
     * @param entityId    Entity ID'si
     * @return Log listesi
     */
    @Transactional(readOnly = true)
    public List<AuditLogEntity> getEntityLogs(String entityClass, String entityId) {
        QAuditLogEntity q = QAuditLogEntity.auditLogEntity;
        Predicate predicate = q.entityClass.eq(entityClass)
                .and(q.entityId.eq(entityId))
                .and(q.visible.isTrue());
        ObjectCore.ListResult<AuditLogEntity> result = ObjectCore.list(q, predicate, null);
        return result.getData();
    }
}
