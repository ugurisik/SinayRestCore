package com.sinay.core.server.timetest.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metod çalışma süresini test eden anotasyon.
 * <p>
 * Bu anotasyon ile işaretlenen metodlar için çalışma süresi ölçülür.
 * Belirtilen limit aşılırsa uyarı log'u yazılır.
 * <p>
 * Kullanım:
 * <pre>
 * &#64;TimeTest(ms = 30)  // 30 milisaniye limit
 * public List&lt;User&gt; getAllUsers() {
 *     return userRepository.findAll();
 *     // 30ms'den uzun sürerse WARN log yazılır
 * }
 * </pre>
 * <p>
 * Çıktı örneği:
 * <pre>
 * WARN  c.s.core.timetest.TimeTestAspect - [TIME TEST FAILED] getAllUsers took 45ms (limit: 30ms) at com.example.UserService.getAllUsers(UserService.java:42)
 * </pre>
 * <p>
 * Kullanım senaryoları:
 * <ul>
 *   <li>Performans sorunlarını erken tespit</li>
 *   <li>SLA (Service Level Agreement) takibi</li>
 *   <li>Optimizasyon öncesi/sonrası karşılaştırma</li>
 *   <li>Production'da yavaş metotları izleme</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeTest {

    /**
     * Maksimum çalışma süresi (milisaniye cinsinden).
     * <p>
     * Metod bu süreden uzun çalışırsa uyarı log'u yazılır.
     *
     * @return Maksimum süre (ms)
     */
    long ms();

    /**
     * Log seviyesi.
     * <p>
     * Limit aşılırsa bu seviyede log yazılır.
     * Varsayılan: WARN
     *
     * @return Log seviyesi
     */
    LogLevel level() default LogLevel.WARN;

    /**
     * Log mesajı.
     * <p>
     * Kişiselleştirilmiş mesaj için kullanılır.
     * {0} = metod adı, {1} = geçen süre, {2} = limit
     *
     * @return Log mesajı formatı
     */
    String message() default "[TIME TEST {outcome}] {method} took {actual}ms (limit: {limit}ms) at {location}";

    /**
     * Başarısızlık durumunda exception fırlat.
     * <p>
     * true ise, limit aşılıyor exception fırlatılır.
     * Dikkat: Production için önerilmez.
     *
     * @return Exception fırlat
     */
    boolean throwOnFailure() default false;

    /**
     * Log seviyesi enum'u.
     */
    enum LogLevel {
        TRACE, DEBUG, INFO, WARN, ERROR
    }
}
