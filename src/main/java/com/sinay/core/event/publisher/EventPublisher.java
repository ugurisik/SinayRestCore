package com.sinay.core.event.publisher;

import com.sinay.core.event.event.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Event yayınlama servisi.
 * <p>
 * Domain event'lerini yayınlamak için kullanılır.
 * Spring'in ApplicationEventPublisher'ını kullanır.
 * <p>
 * Kullanım:
 * <pre>
 * // Event oluştur ve yayınla
 * Map&lt;String, Object&gt; payload = Map.of(
 *     "userId", user.getId(),
 *     "email", user.getEmail()
 * );
 * UserRegisteredEvent event = new UserRegisteredEvent("UserRegisteredEvent", payload);
 * eventPublisher.publish(event);
 *
 * // Veya builder ile
 * eventPublisher.publish("UserRegisteredEvent", payload);
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * Event yayınlar.
     *
     * @param event Yayınlanacak event
     */
    @Async
    public void publish(DomainEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
            log.debug("Event published: type={}, payload={}", event.getEventType(), event.getPayload());
        } catch (Exception e) {
            log.error("Failed to publish event: type={}, error={}", event.getEventType(), e.getMessage());
        }
    }

    /**
     * Event yayınlar (builder ile).
     *
     * @param eventType Event tipi
     * @param payload   Event verileri
     */
    @Async
    public void publish(String eventType, Map<String, Object> payload) {
        try {
            DomainEvent event = new DomainEvent(eventType, payload) {
            };
            publish(event);
        } catch (Exception e) {
            log.error("Failed to publish event: type={}, error={}", eventType, e.getMessage());
        }
    }

    /**
     * Event yayınlar (senkron).
     * <p>
     * Asenkron yayın uygun değilse kullanılır.
     *
     * @param event Yayınlanacak event
     */
    public void publishSync(DomainEvent event) {
        try {
            applicationEventPublisher.publishEvent(event);
            log.debug("Event published (sync): type={}, payload={}", event.getEventType(), event.getPayload());
        } catch (Exception e) {
            log.error("Failed to publish event (sync): type={}, error={}", event.getEventType(), e.getMessage());
        }
    }

    /**
     * Event yayınlar (senkron, builder ile).
     *
     * @param eventType Event tipi
     * @param payload   Event verileri
     */
    public void publishSync(String eventType, Map<String, Object> payload) {
        try {
            DomainEvent event = new DomainEvent(eventType, payload) {
            };
            publishSync(event);
        } catch (Exception e) {
            log.error("Failed to publish event (sync): type={}, error={}", eventType, e.getMessage());
        }
    }
}
