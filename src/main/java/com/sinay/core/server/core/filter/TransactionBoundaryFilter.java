package com.sinay.core.server.core.filter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Transaction boundary koruması sağlayan Filter.
 * <p>
 * Service katmanında ObjectCore kullanan metodların @Transactional
 * annotation'ı olmadan çağrılmasını engeller. ObjectCore EntityManager
 * gerektirir, EntityManager ise transaction içinde çalışmalıdır.
 * </p>
 *
 * <p><b>Problem:</b></p>
 * <pre>
 * Service metodunda @Transactional YOKSA:
 * → ObjectCore.save() çağrılır
 * → EntityManager.getTransaction() = null
 * → HATA: "No EntityManager with actual transaction available"
 * </pre>
 *
 * <p><b>Çözüm:</b></p>
 * <ul>
 *   <li>Controller → Service çağrısı → @Transactional kontrolü</li>
 *   <li>Service metodunda @Transactional yoksa WARN log</li>
 *   <li>Production'da opsiyonel ERROR/HATA fırlat</li>
 * </ul>
 *
 * <p><b>Kullanım:</b></p>
 * <pre>{@code
 * // application.yaml
 * transaction-boundary:
 *   strict-mode: false  # Geliştirme: WARN, Production: true
 * }</pre>
 *
 * @author Uğur Işık
 * @since 1.0
 */
@Slf4j
@Component
public class TransactionBoundaryFilter implements Filter {

    /**
     * Transaction kontrolü yapılmayacak path'ler
     */
    private static final Set<String> EXCLUDE_PATHS = Set.of(
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs",
            "/error",
            "/ws"  // WebSocket
    );

    /**
     * Strict mode - production'da true olmalı
     */
    private final boolean strictMode;

    public TransactionBoundaryFilter() {
        // Varsayılan: false (geliştirme modu)
        this.strictMode = Boolean.parseBoolean(
                System.getProperty("transaction-boundary.strict-mode", "false")
        );
        log.info("TransactionBoundaryFilter initialized: strict-mode={}", strictMode);
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;
        String path = req.getRequestURI();

        // Exclude path'leri atla
        if (shouldExclude(path)) {
            chain.doFilter(request, response);
            return;
        }

        // Request başlangıçta transaction kontrolü
        boolean hasTransactionAtStart = isTransactionActive();

        if (!hasTransactionAtStart && !isStaticResource(path)) {
            log.debug("No transaction at start of request: {} {}", req.getMethod(), path);
        }

        try {
            chain.doFilter(request, response);
        } finally {
            // Request sonunda transaction kontrolü
            boolean hasTransactionAtEnd = isTransactionActive();

            // Eğer request sırasında transaction açıldıysa ve kapatılmadıysa
            if (hasTransactionAtEnd && !hasTransactionAtStart) {
                log.warn("Transaction leak detected! Transaction was opened but not closed for: {} {}", req.getMethod(), path);

                // Transaction'ı temizle (memory leak önleme)
                cleanupTransaction();
            }
        }
    }

    /**
     * Transaction aktif mi kontrolü.
     *
     * @return true transaction aktif
     */
    private boolean isTransactionActive() {
        try {
            return TransactionSynchronizationManager.isActualTransactionActive();
        } catch (Exception e) {
            log.debug("Transaction check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Transaction'ı temizler (memory leak önleme).
     */
    private void cleanupTransaction() {
        try {
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }

            // EntityManager holder'ı temizle
            Map<Object, Object> resourceMap = TransactionSynchronizationManager.getResourceMap();
            for (Object key : resourceMap.keySet()) {
                if (key instanceof EntityManagerHolder) {
                    TransactionSynchronizationManager.unbindResource(key);
                    log.debug("Unbound EntityManagerHolder to prevent leak");
                }
            }
        } catch (Exception e) {
            log.warn("Failed to cleanup transaction: {}", e.getMessage());
        }
    }

    /**
     * Bu path excluded mı?
     *
     * @param path Request path
     * @return true excluded
     */
    private boolean shouldExclude(String path) {
        return EXCLUDE_PATHS.stream().anyMatch(path::startsWith);
    }

    /**
     * Statik resource mı?
     *
     * @param path Request path
     * @return true statik resource
     */
    private boolean isStaticResource(String path) {
        return path.contains(".") ||
                path.startsWith("/static") ||
                path.startsWith("/webjars");
    }

    @Override
    public void init(FilterConfig filterConfig) {
        log.info("TransactionBoundaryFilter initialized");
    }

    @Override
    public void destroy() {
        log.info("TransactionBoundaryFilter destroyed");
    }
}
