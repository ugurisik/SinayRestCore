package com.sinay.core.scheduler.service;

import com.sinay.core.core.ObjectCore;
import com.sinay.core.scheduler.entity.JobLockEntity;
import com.sinay.core.scheduler.entity.QJobLockEntity;
import com.querydsl.core.types.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Job lock servisi.
 * <p>
 * Distributed lock işlemlerini yönetir.
 * <p>
 * Lock alma adımları:
 * <ol>
 *   <li>Job için mevcut lock kontrol et</li>
 *   <li>Lock yoksa veya expire olduysa yeni lock oluştur</li>
 *   <li>Lock varsa ve aktifse, başka instance çalışamaz</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobLockService {
    // Repository kaldırıldı - ObjectCore kullanıyoruz

    /**
     * Instance ID.
     * <p>
     * Her server instance'ı için benzersiz kimlik.
     * Hostname + UUID kombinasyonu kullanılır.
     */
    private static String INSTANCE_ID;

    static {
        try {
            String hostname = InetAddress.getLocalHost().getHostName();
            INSTANCE_ID = hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
        } catch (Exception e) {
            INSTANCE_ID = "unknown-" + UUID.randomUUID().toString().substring(0, 8);
        }
    }

    /**
     * Job için lock almaya çalışır.
     *
     * @param jobName          Job adı
     * @param timeoutMinutes   Lock timeout (dakika)
     * @param forceExpireLock Expire olmuş lock'u zorla al
     * @return true ise lock alındı, false ise başka instance tarafından çalıştırılıyor
     */
    @Transactional
    public boolean tryAcquireLock(String jobName, int timeoutMinutes, boolean forceExpireLock) {
        // Expire olmuş lock'ları temizle
        QJobLockEntity q = QJobLockEntity.jobLockEntity;
        Predicate expiredPredicate = q.lockedUntil.before(LocalDateTime.now()).and(q.isActive.isTrue());
        ObjectCore.ListResult<JobLockEntity> expiredLocks = ObjectCore.list(q, expiredPredicate, null);
        for (JobLockEntity expired : expiredLocks.getData()) {
            expired.setIsActive(false);
            ObjectCore.save(expired);
        }

        // Mevcut lock'ı kontrol et
        Predicate activePredicate = q.jobName.eq(jobName).and(q.isActive.isTrue()).and(q.visible.isTrue());
        ObjectCore.Result<JobLockEntity> existingLockResult = ObjectCore.findOne(q, activePredicate);

        if (existingLockResult.isSuccess()) {
            JobLockEntity lock = existingLockResult.getData();

            // Aktif lock ve bizim instance'ımız mı?
            if (INSTANCE_ID.equals(lock.getInstanceId())) {
                // Bizim lock'ımız, yenile
                lock.renew(timeoutMinutes);
                ObjectCore.save(lock);
                log.debug("Lock renewed: job={}, instance={}", jobName, INSTANCE_ID);
                return true;
            }

            // Başka instance'ın lock'ı ve hala aktif mi?
            if (!lock.isExpired()) {
                log.debug("Lock already held: job={}, holder={}", jobName, lock.getInstanceId());
                return false;
            }

            // Expire olmuş lock, zorla al
            if (forceExpireLock) {
                lock.setIsActive(false);
                ObjectCore.save(lock);
                log.info("Force released expired lock: job={}, holder={}", jobName, lock.getInstanceId());
            } else {
                return false;
            }
        }

        // Yeni lock oluştur
        try {
            JobLockEntity newLock = JobLockEntity.builder()
                    .jobName(jobName)
                    .instanceId(INSTANCE_ID)
                    .lockedAt(LocalDateTime.now())
                    .lockedUntil(LocalDateTime.now().plusMinutes(timeoutMinutes))
                    .isActive(true)
                    .build();

            ObjectCore.save(newLock);
            log.info("Lock acquired: job={}, instance={}, timeout={}min", jobName, INSTANCE_ID, timeoutMinutes);
            return true;
        } catch (ObjectOptimisticLockingFailureException e) {
            // Concurrent lock alma denemesi
            log.warn("Concurrent lock attempt: job={}", jobName);
            return false;
        }
    }

    /**
     * Job lock'ını serbest bırakır.
     *
     * @param jobName Job adı
     */
    @Transactional
    public void releaseLock(String jobName) {
        QJobLockEntity q = QJobLockEntity.jobLockEntity;
        Predicate predicate = q.jobName.eq(jobName).and(q.isActive.isTrue()).and(q.visible.isTrue());
        ObjectCore.Result<JobLockEntity> lockResult = ObjectCore.findOne(q, predicate);

        if (lockResult.isSuccess()) {
            JobLockEntity jobLock = lockResult.getData();

            // Sadece bizim lock'ımızı serbest bırak
            if (INSTANCE_ID.equals(jobLock.getInstanceId())) {
                jobLock.release();
                ObjectCore.save(jobLock);
                log.debug("Lock released: job={}, instance={}", jobName, INSTANCE_ID);
            } else {
                log.warn("Attempted to release lock held by another instance: job={}, holder={}",
                        jobName, jobLock.getInstanceId());
            }
        }
    }

    /**
     * Job çalışma bilgilerini günceller.
     *
     * @param jobName      Job adı
     * @param durationMs   Çalışma süresi
     * @param success      Başarılı mı?
     * @param errorMessage Hata mesajı (başarısız ise)
     */
    @Transactional
    public void updateExecutionInfo(String jobName, Long durationMs, Boolean success, String errorMessage) {
        ObjectCore.Result<JobLockEntity> lockResult = ObjectCore.getByField(JobLockEntity.class, "jobName", jobName);

        if (lockResult.isSuccess()) {
            JobLockEntity jobLock = lockResult.getData();
            jobLock.updateExecutionInfo(durationMs, success, errorMessage);
            ObjectCore.save(jobLock);
            log.debug("Execution info updated: job={}, success={}, duration={}ms",
                    jobName, success, durationMs);
        }
    }

    /**
     * Job lock bilgilerini döndürür.
     *
     * @param jobName Job adı
     * @return Lock bilgisi
     */
    @Transactional(readOnly = true)
    public JobLockEntity getLockInfo(String jobName) {
        ObjectCore.Result<JobLockEntity> result = ObjectCore.getByField(JobLockEntity.class, "jobName", jobName);
        return result.isSuccess() ? result.getData() : null;
    }

    /**
     * Instance ID'yi döndürür.
     *
     * @return Instance ID
     */
    public String getInstanceId() {
        return INSTANCE_ID;
    }
}
