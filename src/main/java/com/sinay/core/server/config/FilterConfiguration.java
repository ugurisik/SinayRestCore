package com.sinay.core.server.config;

import com.sinay.core.server.core.filter.TransactionBoundaryFilter;
import com.sinay.core.server.filter.RequestLoggingFilter;
import com.sinay.core.server.ratelimit.filter.GlobalRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Servlet Filter'ları kaydeden konfigürasyon sınıfı.
 * <p>
 * Filter'ların çalışma sırası önemlidir. Sıra:
 * </p>
 * <ol>
 *   <li>RequestLoggingFilter - İstek loglama</li>
 *   <li>TransactionBoundaryFilter - Transaction kontrolü</li>
 *   <li>GlobalRateLimitFilter - Rate limiting</li>
 *   <li>JwtAuthenticationFilter - JWT doğrulama (SecurityConfig içinde)</li>
 * </ol>
 *
 * @author Uğur Işıkon
 * @since 1.0
 */
@Configuration
@RequiredArgsConstructor
public class FilterConfiguration {

    private final RequestLoggingFilter requestLoggingFilter;
    private final TransactionBoundaryFilter transactionBoundaryFilter;
    private final GlobalRateLimitFilter globalRateLimitFilter;

    /**
     * Request Logging Filter - Her isteği loglar.
     */
    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> requestLoggingFilterRegistration() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>(requestLoggingFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1); // En önce çalışır
        registration.setName("RequestLoggingFilter");
        return registration;
    }

    /**
     * Transaction Boundary Filter - Transaction kontrolü yapar.
     */
    @Bean
    public FilterRegistrationBean<TransactionBoundaryFilter> transactionBoundaryFilterRegistration() {
        FilterRegistrationBean<TransactionBoundaryFilter> registration = new FilterRegistrationBean<>(transactionBoundaryFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        registration.setName("TransactionBoundaryFilter");
        return registration;
    }

    /**
     * Global Rate Limit Filter - Rate limiting yapar.
     */
    @Bean
    public FilterRegistrationBean<GlobalRateLimitFilter> globalRateLimitFilterRegistration() {
        FilterRegistrationBean<GlobalRateLimitFilter> registration = new FilterRegistrationBean<>(globalRateLimitFilter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(3);
        registration.setName("GlobalRateLimitFilter");
        return registration;
    }
}
