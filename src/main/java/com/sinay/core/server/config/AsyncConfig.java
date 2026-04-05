package com.sinay.core.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Asenkron işlem konfigürasyonu.
 * <p>
 * Audit log gibi arka planda çalışması gereken işlemler için
 * thread pool yönetimi sağlar.
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * Audit log işlemleri için thread pool.
     * <p>
     * - Core pool size: 2 (her an aktif olacak thread sayısı)
     * - Max pool size: 5 (maksimum thread sayısı)
     * - Queue capacity: 100 (bekleme kapasitesi)
     */
    @Bean(name = "auditExecutor")
    public Executor auditExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(5);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("audit-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
