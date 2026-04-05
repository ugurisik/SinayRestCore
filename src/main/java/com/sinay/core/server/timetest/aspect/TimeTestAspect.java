package com.sinay.core.server.timetest.aspect;

import com.sinay.core.server.timetest.annotation.TimeTest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.text.MessageFormat;

/**
 * Metod çalışma süresini test eden AOP aspect.
 * <p>
 * @TimeTest anotasyonu ile işaretlenen metodları yakalar ve çalışma süresini ölçer.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>Nanosecond hassasiyetinde zamanlama</li>
 *   <li>Limit aşılıyorsa log yazar</li>
 *   <li>İsteğe bağlı exception fırlatabilir</li>
 *   <li>Metod konum bilgisi içerir</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class TimeTestAspect {

    /**
     * @TimeTest anotasyonunu işler.
     * <p>
     * Metodu çalıştırır ve süresini ölçer.
     */
    @Around("@annotation(timeTest)")
    public Object handleTimeTest(ProceedingJoinPoint joinPoint, TimeTest timeTest) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Başlangıç zamanı
        long startTime = System.nanoTime();

        Object result = null;
        long durationMs = 0;
        boolean success = false;

        try {
            // Metodu çalıştır
            result = joinPoint.proceed();
            success = true;

            // Süreyi hesapla
            long endTime = System.nanoTime();
            durationMs = (endTime - startTime) / 1_000_000; // Nanosaniye -> milisaniye

            // Limit kontrolü
            if (durationMs > timeTest.ms()) {
                logTimeTestExceeded(timeTest, method, durationMs, joinPoint.getSignature().toString());
            } else {
                logTimeTestPassed(timeTest, method, durationMs);
            }

            return result;
        } catch (Exception e) {
            // Hata durumunda da süre ölçülür
            long endTime = System.nanoTime();
            durationMs = (endTime - startTime) / 1_000_000;

            if (durationMs > timeTest.ms()) {
                logTimeTestExceeded(timeTest, method, durationMs, joinPoint.getSignature().toString());
            }

            throw e;
        } finally {
            // throwOnFailure kontrolü
            if (success && durationMs > timeTest.ms() && timeTest.throwOnFailure()) {
                throw new TimeTestExceededException(
                        String.format("Method execution time exceeded limit: %dms (limit: %dms)",
                                durationMs, timeTest.ms())
                );
            }
        }
    }

    /**
     * Limit aşıldığında log yazar.
     */
    private void logTimeTestExceeded(TimeTest timeTest, Method method, long durationMs, String location) {
        String message = formatMessage(timeTest.message(), method.getName(), durationMs, timeTest.ms(), location, "FAILED");

        switch (timeTest.level()) {
            case TRACE:
                if (log.isTraceEnabled()) {
                    log.trace(message);
                }
                break;
            case DEBUG:
                if (log.isDebugEnabled()) {
                    log.debug(message);
                }
                break;
            case INFO:
                log.info(message);
                break;
            case WARN:
                log.warn(message);
                break;
            case ERROR:
                log.error(message);
                break;
        }

        // Ek bilgi: stack trace
        if (timeTest.level() == TimeTest.LogLevel.WARN || timeTest.level() == TimeTest.LogLevel.ERROR) {
            String methodLocation = method.getDeclaringClass().getName() + "." + method.getName();
            int lineNumber = getLineNumber(method);
            if (lineNumber > 0) {
                log.warn("Location: {}:{} (TimeTest limit: {}ms, actual: {}ms)",
                        methodLocation, lineNumber, timeTest.ms(), durationMs);
            }
        }
    }

    /**
     * Limit aşılmadığında log yazar (opsiyonel DEBUG seviyesinde).
     */
    private void logTimeTestPassed(TimeTest timeTest, Method method, long durationMs) {
        if (log.isDebugEnabled()) {
            String message = formatMessage(
                    "[TIME TEST PASSED] {method} took {actual}ms (limit: {limit}ms)",
                    method.getName(),
                    durationMs,
                    timeTest.ms(),
                    null,
                    "PASSED"
            );
            log.debug(message);
        }
    }

    /**
     * Log mesajını formatlar.
     */
    private String formatMessage(String template, String methodName, long actualMs, long limitMs, String location, String outcome) {
        try {
            return MessageFormat.format(template
                    .replace("{method}", "{0}")
                    .replace("{actual}", "{1}")
                    .replace("{limit}", "{2}")
                    .replace("{location}", "{3}")
                    .replace("{outcome}", "{4}"),
                    methodName, actualMs, limitMs, location != null ? location : "unknown", outcome);
        } catch (Exception e) {
            return String.format("[TIME TEST %s] %s took %dms (limit: %dms)",
                    outcome, methodName, actualMs, limitMs);
        }
    }

    /**
     * Metodun satır numarasını alır (basit implementasyon).
     * <p>
     * Not: Gerçek satır numarası için bytecode parsing gerekir.
     */
    private int getLineNumber(Method method) {
        try {
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            for (StackTraceElement element : stackTrace) {
                if (element.getMethodName().equals(method.getName()) &&
                        element.getClassName().equals(method.getDeclaringClass().getName())) {
                    return element.getLineNumber();
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    /**
     * TimeTest limiti aşıldığında fırlatılan exception.
     */
    public static class TimeTestExceededException extends RuntimeException {
        public TimeTestExceededException(String message) {
            super(message);
        }
    }
}
