package com.sinay.core.server.scheduler.aspect;

import com.sinay.core.server.scheduler.annotation.ScheduledJob;
import com.sinay.core.server.scheduler.service.JobLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

/**
 * Zamanlanmış iş anotasyonu için AOP aspect.
 * <p>
 * @ScheduledJob anotasyonu ile işaretlenen metodları zamanlar ve distributed lock uygular.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>Cron expression ile zamanlama</li>
 *   <li>Fixed rate/delay ile zamanlama</li>
 *   <li>Distributed lock ile cluster güvenliği</li>
 *   <li>Job çalışma istatistikleri</li>
 *   <li>Hata yönetimi</li>
 * </ul>
 * <p>
 * Not: Bu aspect job'ları zamanlamak için kullanılır.
 * Spring'in @Scheduled anotasyonu ile birlikte kullanılabilir.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class ScheduledJobAspect {

    private final JobLockService jobLockService;

    /**
     * @ScheduledJob anotasyonunu işler.
     * <p>
     * Metodu belirtilen zamanda çalıştırır ve lock mekanizması uygular.
     */
    @Around("@annotation(scheduledJob)")
    public Object handleScheduledJob(ProceedingJoinPoint joinPoint, ScheduledJob scheduledJob) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Job adı belirle
        String jobName = scheduledJob.name();
        if (jobName.isEmpty()) {
            jobName = method.getDeclaringClass().getSimpleName() + "." + method.getName();
        }

        // Lock kontrolü
        if (scheduledJob.useLock()) {
            boolean lockAcquired = jobLockService.tryAcquireLock(
                    jobName,
                    scheduledJob.lockTimeoutMinutes(),
                    true // Force expire expired locks
            );

            if (!lockAcquired) {
                log.debug("Job skipped (lock held): {}", jobName);
                return null;
            }
        }

        // Job'u çalıştır
        long startTime = System.currentTimeMillis();
        boolean success = false;
        String errorMessage = null;

        try {
            log.info("Job started: {} (instance: {})", jobName, jobLockService.getInstanceId());

            Object result = joinPoint.proceed();

            success = true;
            long duration = System.currentTimeMillis() - startTime;

            log.info("Job completed: {} (duration: {}ms)", jobName, duration);

            // İstatistikleri güncelle
            if (scheduledJob.useLock()) {
                jobLockService.updateExecutionInfo(jobName, duration, true, null);
            }

            return result;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();

            long duration = System.currentTimeMillis() - startTime;

            log.error("Job failed: {} (duration: {}ms, error: {})", jobName, duration, errorMessage);

            // İstatistikleri güncelle
            if (scheduledJob.useLock()) {
                jobLockService.updateExecutionInfo(jobName, duration, false, errorMessage);
            }

            throw e;
        } finally {
            // Lock'ı serbest bırak
            if (scheduledJob.useLock() && success) {
                jobLockService.releaseLock(jobName);
            }
        }
    }
}
