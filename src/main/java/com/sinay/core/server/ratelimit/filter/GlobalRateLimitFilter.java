package com.sinay.core.server.ratelimit.filter;

import com.sinay.core.server.ratelimit.annotation.RateLimit;
import com.sinay.core.server.ratelimit.config.RateLimitProperties;
import com.sinay.core.server.ratelimit.exception.RateLimitException;
import com.sinay.core.server.ratelimit.key.RateLimitKeyResolver;
import com.sinay.core.server.ratelimit.model.KeyType;
import com.sinay.core.server.ratelimit.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;

/**
 * Global rate limit filter.
 * <p>
 * Tüm endpoint'ler için varsayılan rate limit uygular.
 * @RateLimit annotation varsa, annotation değerini kullanır.
 *
 * Öncelik: RateLimitFilter'dan ÖNCE çalışmalı (HIGHEST_PRECEDENCE)
 */
@Slf4j
@Component
@Order(200)  // Spring Security'den sonra çalışsın
public class GlobalRateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final RateLimitKeyResolver keyResolver;
    private final RateLimitProperties properties;
    private final HandlerMapping handlerMapping;

    // Varsayılan global rate limit annotation
    private static final RateLimit GLOBAL_DEFAULT = new RateLimit() {
        @Override
        public int capacity() {
            return 100; // application.yaml'dan alınabilir
        }

        @Override
        public int refillTokens() {
            return 100;
        }

        @Override
        public int refillDurationMinutes() {
            return 1;
        }

        @Override
        public int banThreshold() {
            return 15;
        }

        @Override
        public int banDurationMinutes() {
            return 10;
        }

        @Override
        public KeyType keyType() {
            return KeyType.IP_AND_USER;
        }

        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return RateLimit.class;
        }
    };

    public GlobalRateLimitFilter(RateLimitService rateLimitService,
                                  RateLimitKeyResolver keyResolver,
                                  RateLimitProperties properties,
                                  @Qualifier("requestMappingHandlerMapping") HandlerMapping handlerMapping) {
        this.rateLimitService = rateLimitService;
        this.keyResolver = keyResolver;
        this.properties = properties;
        this.handlerMapping = handlerMapping;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            // Handler'ı bul
            HandlerExecutionChain chain = handlerMapping.getHandler(request);
            if (chain == null) {
                filterChain.doFilter(request, response);
                return;
            }

            Object handler = chain.getHandler();
            if (!(handler instanceof HandlerMethod handlerMethod)) {
                filterChain.doFilter(request, response);
                return;
            }

            // @RateLimit annotation ara
            RateLimit annotation = AnnotationUtils.findAnnotation(handlerMethod.getMethod(), RateLimit.class);

            // Annotation class seviyesinde ara
            if (annotation == null) {
                annotation = AnnotationUtils.findAnnotation(handlerMethod.getBeanType(), RateLimit.class);
            }

            if (annotation != null) {
                // Annotation varsa → onu kullan
                String key = keyResolver.resolveKey(annotation.keyType());
                if (key != null) {
                    log.debug("Using @RateLimit annotation for: {}", request.getRequestURI());
                    rateLimitService.checkRateLimit(key, annotation);
                } else {
                    // Key null ise fallback olarak IP kullan
                    String fallbackKey = keyResolver.resolveKey(KeyType.IP);
                    log.warn("Annotation key is null for {}, using IP fallback: {}", request.getRequestURI(), fallbackKey);
                    if (fallbackKey != null) {
                        rateLimitService.checkRateLimit(fallbackKey, annotation);
                    }
                }
            } else {
                // Annotation YOKSA → global default kullan
                String key = keyResolver.resolveKey(KeyType.IP_AND_USER);
                if (key != null) {
                    log.debug("Using global rate limit for: {}", request.getRequestURI());
                    rateLimitService.checkRateLimit(key, GLOBAL_DEFAULT);
                } else {
                    // Key null ise IP fallback
                    String fallbackKey = keyResolver.resolveKey(KeyType.IP);
                    log.warn("Global key is null for {}, using IP fallback: {}", request.getRequestURI(), fallbackKey);
                    if (fallbackKey != null) {
                        rateLimitService.checkRateLimit(fallbackKey, GLOBAL_DEFAULT);
                    }
                }
            }

            filterChain.doFilter(request, response);

        } catch (RateLimitException e) {
            handleRateLimitException(response, e);
        } catch (Exception e) {
            log.error("Global rate limit filter error", e);
            filterChain.doFilter(request, response);
        }
    }

    private void handleRateLimitException(HttpServletResponse response, RateLimitException e) throws IOException {
        response.setStatus(429); // HTTP 429 Too Many Requests
        response.setContentType("application/json;charset=UTF-8");

        int retryAfterSeconds = e.getRetryAfterSeconds();
        if (retryAfterSeconds > 0) {
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        }

        String jsonResponse = String.format("{\"success\":false,\"message\":\"%s\"}", escapeJson(e.getMessage()));
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();

        log.warn("Global rate limit triggered: {}", e.getMessage());
    }

    private String escapeJson(String message) {
        if (message == null) return "";
        return message.replace("\\", "\\\\")
                     .replace("\"", "\\\"")
                     .replace("\n", "\\n")
                     .replace("\r", "\\r")
                     .replace("\t", "\\t");
    }
}
