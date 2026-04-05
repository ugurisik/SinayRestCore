package com.sinay.core.server.event.aspect;

import com.sinay.core.server.event.annotation.PublishEvent;
import com.sinay.core.server.event.event.DomainEvent;
import com.sinay.core.server.event.publisher.EventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Event yayınlama anotasyonu için AOP aspect.
 * <p>
 * @PublishEvent anotasyonu ile işaretlenen metodları yakalar ve event yayınlar.
 * <p>
 * Özellikler:
 * <ul>
 *   <li>Metot başarılı olduğunda event yayınlar</li>
 *   <li>Asenkron veya senkron yayınlayabilir</li>
 *   <li>Metot sonucunu ve parametrelerini payload'a ekleyebilir</li>
 *   <li>publishOnError = true ise hata durumunda da yayınlar</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class EventPublishAspect {

    private final EventPublisher eventPublisher;

    /**
     * @PublishEvent anotasyonunu işler.
     * <p>
     * Metod çalıştırılır ve sonra event yayınlanır.
     */
    @Around("@annotation(publishEvent)")
    public Object handlePublishEvent(ProceedingJoinPoint joinPoint, PublishEvent publishEvent) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        Object result = null;
        Throwable throwable = null;
        boolean success = false;

        try {
            // Metodu çalıştır
            result = joinPoint.proceed();
            success = true;

            // Event oluştur ve yayınla
            if (success || publishEvent.publishOnError()) {
                DomainEvent event = createEvent(publishEvent, method, joinPoint.getArgs(), result, null);
                publishEvent(publishEvent, event);
            }

            return result;
        } catch (Exception e) {
            throwable = e;

            // Hata durumunda da yayınla
            if (publishEvent.publishOnError()) {
                DomainEvent event = createEvent(publishEvent, method, joinPoint.getArgs(), null, e);
                publishEvent(publishEvent, event);
            }

            throw e;
        }
    }

    /**
     * Event oluşturur.
     */
    private DomainEvent createEvent(PublishEvent publishEvent, Method method, Object[] args, Object result, Throwable error) {
        Map<String, Object> payload = new HashMap<>();

        // Metot parametrelerini ekle
        if (publishEvent.includeArguments()) {
            String[] paramNames = getParameterNames(method);
            for (int i = 0; i < args.length && i < paramNames.length; i++) {
                payload.put(paramNames[i], args[i]);
            }
        }

        // Sonucu ekle
        if (publishEvent.includeResult() && result != null) {
            payload.put("result", result);
        }

        // Hata bilgisi ekle
        if (error != null) {
            payload.put("error", error.getClass().getSimpleName());
            payload.put("errorMessage", error.getMessage());
        }

        // Metot bilgisi ekle
        payload.put("_methodName", method.getName());
        payload.put("_className", method.getDeclaringClass().getSimpleName());

        // Event tipi belirle
        String eventType = publishEvent.eventType() != DomainEvent.class
                ? publishEvent.eventType().getSimpleName()
                : method.getName() + "Event";

        // Event builder metodu varsa kullan
        if (!publishEvent.eventBuilder().isEmpty() && result != null) {
            return invokeEventBuilder(result, publishEvent.eventBuilder(), eventType, payload);
        }

        // Varsayılan event oluştur
        return new DomainEvent(eventType, payload) {
        };
    }

    /**
     * Event yayınlar.
     */
    private void publishEvent(PublishEvent publishEvent, DomainEvent event) {
        try {
            if (publishEvent.async()) {
                eventPublisher.publish(event);
            } else {
                eventPublisher.publishSync(event);
            }
        } catch (Exception e) {
            log.error("Failed to publish event: type={}, error={}", event.getEventType(), e.getMessage());
        }
    }

    /**
     * Event builder metodunu çağırır.
     */
    private DomainEvent invokeEventBuilder(Object result, String builderMethod, String eventType, Map<String, Object> payload) {
        try {
            Method method = result.getClass().getMethod(builderMethod);
            Object event = method.invoke(result);

            if (event instanceof DomainEvent) {
                return (DomainEvent) event;
            } else {
                log.warn("Event builder method did not return DomainEvent: {}.{}", result.getClass().getSimpleName(), builderMethod);
                return new DomainEvent(eventType, payload) {
                };
            }
        } catch (Exception e) {
            log.error("Failed to invoke event builder: {}.{}", result.getClass().getSimpleName(), builderMethod);
            return new DomainEvent(eventType, payload) {
            };
        }
    }

    /**
     * Metod parametre isimlerini döndürür (basit implementasyon).
     */
    private String[] getParameterNames(Method method) {
        int paramCount = method.getParameterCount();
        String[] names = new String[paramCount];
        for (int i = 0; i < paramCount; i++) {
            names[i] = "arg" + i;
        }
        return names;
    }
}
