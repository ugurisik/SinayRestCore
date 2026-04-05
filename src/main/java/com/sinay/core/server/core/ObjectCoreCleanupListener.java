package com.sinay.core.server.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.support.RequestHandledEvent;

/**
 * ObjectCore ThreadLocal temizleyicisi.
 * <p>
 * Her HTTP request sonunda ObjectCore'un ThreadLocal cache'ini temizler.
 * Memory leak önler.
 *
 * @see ObjectCore#clearEntityManager()
 */
@Component
@ConditionalOnClass(ObjectCore.class)
public class ObjectCoreCleanupListener {

    private static final Logger log = LoggerFactory.getLogger(ObjectCoreCleanupListener.class);

    /**
     * Her request sonunda çağrılır.
     * <p>
     * Spring'in RequestHandledEvent'i tetiklenir:
     * <ul>
     *   <li>Request tamamlandığında</li>
     *   <li>Response gönderildiğinde</li>
     *   <li>Hata olsa bile</li>
     * </ul>
     */
    @EventListener
    @Async  // Ana thread'i bloklama
    public void onRequestHandled(RequestHandledEvent event) {
        try {
            ObjectCore.clearEntityManager();
            log.trace("ObjectCore ThreadLocal temizlendi");
        } catch (Exception e) {
            // Temizlik hatası request'i engellemesin
            log.trace("ObjectCore temizlik hatası: {}", e.getMessage());
        }
    }
}
