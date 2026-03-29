package com.sinay.core.audit.aspect;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinay.core.audit.annotation.AuditLog;
import com.sinay.core.audit.service.AuditService;
import com.sinay.core.security.userdetails.AppUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Audit log anotasyonu için AOP aspect.
 * <p>
 * @AuditLog anotasyonu ile işaretlenen metodları yakalar ve audit log kaydı oluşturur.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>Kullanıcı bilgilerini otomatik alır (SecurityContext'ten)</li>
 *   <li>IP adresi ve User Agent'ı alır (HttpServletRequest'ten)</li>
 *   <li>Metod parametrelerini ve sonucunu loglar</li>
 *   <li>Hata durumlarını da loglar</li>
 *   <li>Asenkron loglama (performans için)</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    /**
     * @AuditLog anotasyonunu işler.
     * <p>
     * Metod çalıştırılır, sonra (veya hata durumunda) audit log kaydı oluşturulur.
     */
    @Around("@annotation(auditLog)")
    public Object handleAuditLog(ProceedingJoinPoint joinPoint, AuditLog auditLog) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Audit bilgilerini topla
        AuditContext context = collectAuditInfo(auditLog, method, joinPoint.getArgs());

        Object result = null;
        boolean success = false;
        String errorMessage = null;

        try {
            // Metodu çalıştır
            result = joinPoint.proceed();
            success = true;

            // Entity ID'si result'tan alınabilirse
            if (auditLog.entityId().contains("result")) {
                context.entityId = evaluateEntityId(auditLog.entityId(), method, joinPoint.getArgs(), result);
            }

            return result;
        } catch (Exception e) {
            success = false;
            errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
            throw e;
        } finally {
            // Audit log kaydı oluştur
            createAuditLog(context, success, errorMessage, result);
        }
    }

    /**
     * Audit bilgilerini toplar.
     */
    private AuditContext collectAuditInfo(AuditLog auditLog, Method method, Object[] args) {
        AuditContext context = new AuditContext();

        // Kullanıcı bilgileri
        if (auditLog.includeUserId()) {
            context.userId = getCurrentUserId();
            context.username = getCurrentUsername();
        }

        // Action
        context.action = auditLog.action();

        // Entity bilgileri
        context.entityClass = auditLog.entityType().isEmpty()
                ? null
                : "com.sinay.core.entity." + auditLog.entityType();
        if (!auditLog.entityId().isEmpty() && !auditLog.entityId().contains("result")) {
            context.entityId = evaluateEntityId(auditLog.entityId(), method, args, null);
        }

        // HTTP bilgileri
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            if (auditLog.includeIpAddress()) {
                context.ipAddress = getClientIpAddress(request);
            }
            if (auditLog.includeUserAgent()) {
                context.userAgent = request.getHeader("User-Agent");
            }
        }

        // Mesaj
        context.message = auditLog.message();

        // Değişiklik bilgisi (parametreleri JSON olarak kaydet)
        context.logData = serializeChanges(args);

        return context;
    }

    /**
     * Audit log kaydı oluşturur.
     */
    private void createAuditLog(AuditContext context, boolean success, String errorMessage, Object result) {
        try {
            auditService.createLog(
                    context.userId,
                    context.username,
                    context.action,
                    context.entityClass,
                    context.entityId,
                    context.logData,
                    context.ipAddress,
                    context.userAgent,
                    success,
                    errorMessage,
                    context.message
            );
        } catch (Exception e) {
            log.error("Failed to create audit log: action={}, entity={}, error={}",
                    context.action, context.entityClass, e.getMessage());
        }
    }

    /**
     * Şu anki kullanıcının ID'sini döndürür.
     */
    private String getCurrentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails) {
                AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
                return userDetails.getId().toString();
            }
        } catch (Exception e) {
            log.trace("Failed to get current user ID: {}", e.getMessage());
        }
        return null; // Anonymous kullanıcı için null
    }

    /**
     * Şu anki kullanıcının kullanıcı adını döndürür.
     */
    private String getCurrentUsername() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof AppUserDetails) {
                AppUserDetails userDetails = (AppUserDetails) authentication.getPrincipal();
                return userDetails.getUsername();
            }
            return authentication != null ? authentication.getName() : "anonymous";
        } catch (Exception e) {
            log.trace("Failed to get current username: {}", e.getMessage());
            return "anonymous";
        }
    }

    /**
     * Şu anki HTTP isteğini döndürür.
     */
    private HttpServletRequest getCurrentRequest() {
        try {
            ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            return attributes != null ? attributes.getRequest() : null;
        } catch (Exception e) {
            log.trace("Failed to get current request: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Client IP adresini döndürür.
     * <p>
     * X-Forwarded-For header'ını kontrol eder (proxy/reverse proxy için).
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // X-Forwarded-For virgülle ayrılmış IP'ler içerebilir, ilkini al
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * Entity ID'sini SpEL expression'ından değerlendirir.
     * <p>
     * Basit implementasyon - sadece #p0, #p1 vb. parametre referanslarını destekler.
     */
    private String evaluateEntityId(String expression, Method method, Object[] args, Object result) {
        try {
            // Basit parametre referansı (#p0, #id vb.)
            if (expression.startsWith("#")) {
                String varName = expression.substring(1);
                // #p0, #p1 vb.
                if (varName.startsWith("p") && varName.length() > 1) {
                    try {
                        int index = Integer.parseInt(varName.substring(1));
                        if (index >= 0 && index < args.length) {
                            return args[index] != null ? args[index].toString() : null;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
                // #result
                if ("result".equals(varName) && result != null) {
                    return result.toString();
                }
                // Parametre ismiyle
                for (Object arg : args) {
                    if (arg != null) {
                        // toString ile ID'yi al
                        String str = arg.toString();
                        // UUID formatı kontrolü
                        if (str.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) {
                            return str;
                        }
                    }
                }
            }
            return expression;
        } catch (Exception e) {
            log.warn("Failed to evaluate entity ID: expr={}, error={}", expression, e.getMessage());
            return null;
        }
    }

    /**
     * Parametreleri JSON olarak serileştirir.
     * <p>
     * Değişiklik takibi için kullanılır.
     */
    private byte[] serializeChanges(Object[] args) {
        try {
            if (args == null || args.length == 0) {
                return null;
            }

            Map<String, Object> changes = new HashMap<>();
            for (int i = 0; i < args.length; i++) {
                String key = "arg" + i;
                Object value = args[i];

                // Sadece basit tipleri serileştir (recursive sorunları önlemek için)
                if (isSimpleType(value)) {
                    changes.put(key, value);
                } else {
                    changes.put(key, value != null ? value.getClass().getSimpleName() : null);
                }
            }

            return objectMapper.writeValueAsBytes(changes);
        } catch (Exception e) {
            log.trace("Failed to serialize changes: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Basit tip kontrolü.
     */
    private boolean isSimpleType(Object value) {
        if (value == null) {
            return true;
        }
        Class<?> clazz = value.getClass();
        return clazz.isPrimitive() ||
                clazz.equals(String.class) ||
                clazz.equals(Integer.class) ||
                clazz.equals(Long.class) ||
                clazz.equals(Double.class) ||
                clazz.equals(Float.class) ||
                clazz.equals(Boolean.class) ||
                clazz.equals(Character.class) ||
                clazz.equals(Byte.class) ||
                clazz.equals(Short.class) ||
                Number.class.isAssignableFrom(clazz);
    }

    /**
     * Audit bilgileri için inner class.
     */
    private static class AuditContext {
        String userId;
        String username;
        com.sinay.core.audit.enums.AuditAction action;
        String entityClass;
        String entityId;
        byte[] logData;
        String ipAddress;
        String userAgent;
        String message;
    }
}
