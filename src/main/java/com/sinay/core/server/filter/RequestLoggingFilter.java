package com.sinay.core.server.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * HTTP request/response logging filter'i.
 * <p>
 * Her API çağrısını loglar:
 * <ul>
 *   <li>HTTP method, URI</li>
 *   <li>Client IP adresi</li>
 *   <li>Request süresi (ms)</li>
 *   <li>Response status code</li>
 *   <li>Correlation ID (MDC)</li>
 * </ul>
 * <p>
 * MDC (Mapped Diagnostic Context) ile correlation ID tutarak
 * log'larda aynı isteği takip edebilirsiniz.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    private static final String START_TIME_ATTR = "requestStartTime";
    private static final String CORRELATION_ID_MDC = "correlationId";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        // Correlation ID oluştur veya header'dan al
        String correlationId = getOrCreateCorrelationId(request);
        MDC.put(CORRELATION_ID_MDC, correlationId);

        // Başlangıç zamanı
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTR, startTime);

        // Response'a correlation ID ekle
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            // Request'i logla
            logRequest(request);

            // Filter chain'e devam et
            filterChain.doFilter(request, response);

        } finally {
            // Response'u logla
            long duration = System.currentTimeMillis() - startTime;
            logResponse(request, response, duration);

            // MDC'yi temizle (memory leak önleme)
            MDC.remove(CORRELATION_ID_MDC);
        }
    }

    /**
     * Correlation ID oluşturur veya header'dan okur.
     */
    private String getOrCreateCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);

        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString().substring(0, 8);
        }

        return correlationId;
    }

    /**
     * Request'i loglar.
     */
    private void logRequest(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString();
        String ip = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        if (queryString != null) {
            uri = uri + "?" + queryString;
        }

        log.info("→ HTTP {} {} from {} | User-Agent: {}",
            method, uri, ip, userAgent);
    }

    /**
     * Response'u loglar.
     */
    private void logResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            long duration) {

        String method = request.getMethod();
        String uri = request.getRequestURI();
        String ip = getClientIp(request);
        int status = response.getStatus();

        // Status'a göre log seviyesi belirle
        if (status >= 500) {
            log.error("← HTTP {} {} {} from {} in {}ms | Server Error",
                method, uri, status, ip, duration);
        } else if (status >= 400) {
            log.warn("← HTTP {} {} {} from {} in {}ms | Client Error",
                method, uri, status, ip, duration);
        } else if (duration > 1000) {
            log.warn("← HTTP {} {} {} from {} in {}ms | Slow Request",
                method, uri, status, ip, duration);
        } else {
            log.info("← HTTP {} {} {} from {} in {}ms",
                method, uri, status, ip, duration);
        }
    }

    /**
     * Client IP adresini alır.
     * <p>
     * Proxy/reverse proxy arkasında çalışıyorsa X-Forwarded-For header'ını okur.
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");

        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }

        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // X-Forwarded-For virgüllü liste olabilir: "client, proxy1, proxy2"
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Actuator endpoint'lerini loglama
        String uri = request.getRequestURI();
        return uri.startsWith("/actuator") ||
               uri.startsWith("/swagger-ui") ||
               uri.startsWith("/v3/api-docs");
    }
}
