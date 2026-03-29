package com.sinay.core.scheduler.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Zamanlanmış iş (scheduled job) anotasyonu.
 * <p>
 * Bu anotasyon ile işaretlenen metodlar belirli aralıklarla çalıştırılır.
 * Spring'in @Scheduled anotasyonuna benzer, ancak distributed lock özelliği ekler.
 * <p>
 * Kullanım:
 * <pre>
 * &#64;ScheduledJob(
 *     name = "cleanupOldLogs",
 *     cron = "0 0 2 * * ?",  // Her gece 02:00'de
 *     useLock = true
 * )
 * public void cleanupOldLogs() {
 *     // Eski logları temizle
 *     auditService.cleanOldLogs(LocalDateTime.now().minusDays(90));
 * }
 * </pre>
 * <p>
 * Cron expression örnekleri:
 * <ul>
 *   <li>"0 0 * * * *" - Her saat başı</li>
 *   <li>"0 0 2 * * *" - Her gece 02:00'de</li>
 *   <li>"0 0 2 * * MON" - Her Pazartesi 02:00'de</li>
 *   <li>"0 *\\5 * * * *" - Her 5 dakikada bir</li>
 *   <li>"0 0 12 1 * *" - Her ayın 1'i 12:00'de</li>
 * </ul>
 * <p>
 * Distributed lock:
 * <ul>
 *   <li>useLock = true ile cluster ortamında aynı job sadece bir instance tarafından çalıştırılır</li>
 *   <li>lockTimeoutMinutes ile lock süresi belirlenir (varsayılan: 30 dakika)</li>
 *   <li>Job çakışması veya deadlock önlenir</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ScheduledJob {

    /**
     * Job adı.
     * <p>
     * Benzersiz olmalıdır. Lock mekanizması ve loglama için kullanılır.
     *
     * @return Job adı
     */
    String name() default "";

    /**
     * Cron expression.
     * <p>
     * Unix cron formatına benzer, ancak 6 alanlı (saniye dahil).
     * <p>
     * Format: saniye dakika saat gün-ay gün-hafta
     * Örnek: "0 0 2 * * ?" - Her gece 02:00'de
     *
     * @return Cron expression
     */
    String cron() default "";

    /**
     * Sabit oran (milisaniye cinsinden).
     * <p>
     * Her çağrı arasındaki milisaniye sayısı.
     * cron ile birlikte kullanılamaz.
     *
     * @return Sabit oran (ms)
     */
    long fixedRate() default -1;

    /**
     * Sabit gecikme (milisaniye cinsinden).
     * <p>
     * Her bir çağrının başlangıç zamanından bir sonrakinin başlangıcına kadar geçen süre.
     * fixedRate'dan farklı olarak, bir önceki job'ın tamamlanma bekleme süresini dahil etmez.
     *
     * @return Sabit gecikme (ms)
     */
    long fixedDelay() default -1;

    /**
     * İlk gecikme (milisaniye cinsinden).
     * <p>
     * Uygulama başladıktan sonra ilk çalıştırma gecikmesi.
     *
     * @return İlk gecikme (ms)
     */
    long initialDelay() default 0;

    /**
     * Distributed lock kullan.
     * <p>
     * true ise, cluster ortamında aynı job sadece bir instance tarafından çalıştırılır.
     * <p>
     * Veritabanı tabanlı lock mekanizması kullanılır.
     *
     * @return Lock kullanılsın mı?
     */
    boolean useLock() default false;

    /**
     * Lock timeout (dakika cinsinden).
     * <p>
     * Lock alma süresi. Job bu süreden uzun sürerse lock otomatik serbest bırakılır.
     * <p>
     * Varsayılan: 30 dakika
     *
     * @return Lock timeout (dakika)
     */
    int lockTimeoutMinutes() default 30;

    /**
     * Job açıklaması.
     * <p>
     * Job ne yapar? Loglama ve dokümantasyon için.
     *
     * @return Job açıklaması
     */
    String description() default "";
}
