package com.sinay.core.server.core.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * ObjectCore metod çağrımlarında transaction kontrolü yapan Aspect.
 * <p>
 * ObjectCore.save(), getById(), delete() gibi metodlar EntityManager
 * gerektirir. EntityManager ise transaction içinde çalışmalıdır.
 * Bu Aspect, ObjectCore çağrıldığında transaction'ın aktif olup
 * olmadığını kontrol eder.
 * </p>
 *
 * <p><b>Kullanım:</b></p>
 * <pre>{@code
 * @Service
 * public class UserService {
 *
 *     // DOĞRU - Transaction var
 *     @Transactional
 *     public UserResponse getById(UUID id) {
 *         ObjectCore.Result<User> result = ObjectCore.getById(User.class, id);
 *         // Çalışır
 *     }
 *
 *     // YANLIŞ - Transaction YOK
 *     public UserResponse getById(UUID id) {  // @Transactional eksik!
 *         ObjectCore.Result<User> result = ObjectCore.getById(User.class, id);
 *         // HATA: "No EntityManager with actual transaction available"
 *     }
 * }
 * }</pre>
 *
 * <p><b>Not:</b> Geliştirme modunda WARN log, production'da opsiyonel ERROR/HATA.</p>
 *
 * @author Uğur Işık
 * @since 1.0
 */
@Slf4j
@Aspect
@Component
@Order(2) // ResultValidationAspect'ten sonra çalışır
public class ObjectCoreTransactionAspect {

    /**
     * ObjectCore.* metod çağrımlarında transaction kontrolü.
     *
     * @param joinPoint JoinPoint
     */
    @Before("execution(* com.sinay.core.server.core.ObjectCore.*(..))")
    public void checkTransactionBeforeObjectCore(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();

        // Sadece write operasyonlarında kontrol et
        if (isWriteOperation(methodName)) {
            checkTransactionForWrite(methodName);
        } else {
            // Read operasyonlarında loglama
            log.trace("ObjectCore.{} called (read operation)", methodName);
        }
    }

    /**
     * Write operasyonu mu?
     *
     * @param methodName Metod adı
     * @return true write operasyonu
     */
    private boolean isWriteOperation(String methodName) {
        return methodName.equals("save") ||
                methodName.equals("delete") ||
                methodName.equals("hardDelete") ||
                methodName.equals("saveAll") ||
                methodName.equals("saveAllNoAudit") ||
                methodName.startsWith("update") ||
                methodName.startsWith("nativeUpdate");
    }

    /**
     * Write operasyonu için transaction kontrolü.
     *
     * @param methodName Metod adı
     */
    private void checkTransactionForWrite(String methodName) {
        boolean hasTransaction = TransactionSynchronizationManager.isActualTransactionActive();

        if (!hasTransaction) {
            // Stack trace ile çağıran metodu bul
            StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
            String caller = "unknown";

            for (int i = 2; i < Math.min(stackTrace.length, i + 10); i++) {
                String className = stackTrace[i].getClassName();
                if (className.contains("Service") && !className.contains("$")) {
                    caller = stackTrace[i].getClassName() + "." + stackTrace[i].getMethodName();
                    break;
                }
            }

            log.warn("⚠️ TRANSACTION BULUNAMADI! ObjectCore.{}() çağrıldı ama aktif transaction yok! " +
                            "Çağıran: {} | " +
                            "Düzeltmek için: @Transactional annotation ekleyin",
                    methodName, caller);

            // Geliştirme modunda helper mesaj
            log.warn("💡 DÜZELT: {}.{} metoduna @Transactional ekleyin",
                    caller.contains(".") ? caller.substring(0, caller.lastIndexOf('.')) : "Service",
                    caller.contains(".") ? caller.substring(caller.lastIndexOf('.') + 1) : methodName);

            // Strict mode'da exception fırlat
            if (isStrictMode()) {
                throw new IllegalStateException(
                        "ObjectCore." + methodName + "() requires active transaction. " +
                                "Add @Transactional to calling method: " + caller
                );
            }
        } else {
            log.debug("✓ Transaction aktif - ObjectCore.{}() güvenli çalışacak", methodName);
        }
    }

    /**
     * Strict mode mu?
     *
     * @return true strict mode
     */
    private boolean isStrictMode() {
        return Boolean.parseBoolean(
                System.getProperty("transaction-boundary.strict-mode", "false")
        );
    }
}
