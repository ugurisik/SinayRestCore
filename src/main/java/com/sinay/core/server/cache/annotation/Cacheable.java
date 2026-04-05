package com.sinay.core.server.cache.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metodun dönüş değerini cache'e alan anotasyon.
 * <p>
 * Bu anotasyon ile işaretlenen metodlar çalıştırıldığında:
 * <ol>
 *   <li>Önce cache'te sonuç aranır</li>
 *   <li>Cache'te varsa, metod çalıştırılmadan cache'teki değer döndürülür</li>
 *   <li>Cache'te yoksa, metot çalıştırılır ve sonuç cache'e alınır</li>
 * </ol>
 * <p>
 * Kullanım:
 * <pre>
 * &#64;Cacheable(value = "users", key = "#id", ttl = 300)
 * public UserResponse getById(UUID id) {
 *     // Bu metot sadece cache miss durumunda çalışır
 *     return repository.findById(id).map(mapper::toResponse).orElseThrow();
 * }
 * </pre>
 * <p>
 * SpEL (Spring Expression Language) key parametrelerinde kullanılabilir:
 * <ul>
 *   <li>#id - İlk parametrenin id özelliği</li>
 *   <li>#p0 - İlk parametre</li>
 *   <li>#user.email - user parametresinin email özelliği</li>
 *   <li>#root.methodName - Metod adı</li>
 *   <li>#root.args - Tüm parametreler</li>
 * </ul>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Cacheable {

    /**
     * Cache ismi.
     * <p>
     * Genellikle entity veya domain adı (örn: "users", "products", "orders").
     *
     * @return Cache ismi
     */
    String value();

    /**
     * Cache anahtarı (SpEL expression).
     * <p>
     * Belirtilmezse, varsayılan key generator kullanılır.
     * <p>
     * Örnekler:
     * <ul>
     *   <li>"#id" - id parametresi</li>
     *   <li>"#user.id" - user parametresinin id'si</li>
     *   <li>"#p0 + ':' + #p1" - İki parametrenin birleşimi</li>
     * </ul>
     *
     * @return Cache anahtarı expression'ı
     */
    String key() default "";

    /**
     * Cache için TTL (saniye cinsinden).
     * <p>
     * Varsayılan: 300 saniye (5 dakika)
     * 0 veya negatifsesonsuz cache'lenir.
     *
     * @return TTL süresi (saniye)
     */
    int ttl() default 300;

    /**
     * Cache koşulu (SpEL expression).
     * <p>
     * Sadece bu koşul true ise cache'lenir.
     * <p>
     * Örnek: "#result != null" - Sadece non-null sonuçlar cache'lenir
     *
     * @return Cache koşulu
     */
    String condition() default "";

    /**
     * Cache hariç tutma koşulu (SpEL expression).
     * <p>
     * Bu koşul true ise cache'lenmez.
     * <p>
     * Örnek: "#result.size() == 0" - Boş listeler cache'lenmez
     *
     * @return Hariç tutma koşulu
     */
    String unless() default "";
}
