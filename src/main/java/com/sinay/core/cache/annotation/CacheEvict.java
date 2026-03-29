package com.sinay.core.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Cache'ten değer silen anotasyon.
 * <p>
 * Bu anotasyon ile işaretlenen metodlar çalıştırıldıktan sonra:
 * <ul>
 *   <li>Belirtilen cache ve anahtardaki değer silinir (evict)</li>
 *   <li>allEntries = true ise, tüm cache temizlenir</li>
 *   <li>beforeInvocation = true ise, metottan önce silme yapılır</li>
 * </ul>
 * <p>
 * Kullanım:
 * <pre>
 * // Tek bir anahtarı sil
 * &#64;CacheEvict(value = "users", key = "#id")
 * public void delete(UUID id) {
 *     repository.deleteById(id);
 *     // Metottan sonra "users" cache'indeki "id" anahtarı silinir
 * }
 *
 * // Tüm cache'i temizle
 * &#64;CacheEvict(value = "users", allEntries = true)
 * public void clearAllUsersCache() {
 *     // Metottan sonra "users" cache'inin tümü silinir
 * }
 *
 * // Metottan önce sil (hata durumunda bile sil)
 * &#64;CacheEvict(value = "users", key = "#id", beforeInvocation = true)
 * public void updateUser(UUID id, UpdateRequest request) {
 *     // Metottan önce "users" cache'indeki "id" anahtarı silinir
 *     // Bu sayede update sonrası stale data kalmaz
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface CacheEvict {

    /**
     * Cache ismi.
     * <p>
     * @Cacheable ile aynı cache ismi kullanılmalı.
     *
     * @return Cache ismi
     */
    String value();

    /**
     * Silinecek cache anahtarı (SpEL expression).
     * <p>
     * allEntries = true ise bu parametre göz ardı edilir.
     *
     * @return Cache anahtarı expression'ı
     */
    String key() default "";

    /**
     * Tüm cache'i temizle.
     * <p>
     * true ise, belirtilen cache'deki tüm veriler silinir.
     * false ise, sadece belirtilen anahtar silinir.
     *
     * @return true tüm cache temizlenir
     */
    boolean allEntries() default false;

    /**
     * Metottan önce sil.
     * <p>
     * true ise, cache temizleme metottan önce yapılır.
     * <p>
     * Kullanım senaryoları:
     * <ul>
     *   <li>Metot hata fırlatsa bile cache'in temizlenmesini garanti etmek</li>
     *   <li>Update işlemlerinde önce eski veriyi temizlemek</li>
     * </ul>
     *
     * @return true metottan önce silinir
     */
    boolean beforeInvocation() default false;

    /**
     * Cache koşulu (SpEL expression).
     * <p>
     * Sadece bu koşul true ise cache temizlenir.
     * <p>
     * Örnek: "#result > 0" - Sadece pozitif sonuçlarda sil
     *
     * @return Cache koşulu
     */
    String condition() default "";
}
