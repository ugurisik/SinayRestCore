package com.sinay.core.server.event.annotation;

import com.sinay.core.server.event.event.DomainEvent;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Event yayınlama anotasyonu.
 * <p>
 * Bu anotasyon ile işaretlenen metodlar çalıştırıldıktan sonra domain event yayınlanır.
 * <p>
 * Event yayınları asenkron olarak yapılır, böylece ana işlemi yavaşlatmaz.
 * <p>
 * Kullanım:
 * <pre>
 * &#64;PublishEvent(eventType = UserRegisteredEvent.class)
 * public UserResponse register(RegisterRequest request) {
 *     // Kullanıcı kaydı
 *     User user = createUser(request);
 *
 *     // Metot tamamlandıktan sonra UserRegisteredEvent otomatik yayınlanır
 *     return mapper.toResponse(user);
 * }
 * </pre>
 * <p>
 * Event listener tanımlama:
 * <pre>
 * &#64;Component
 * public class NotificationListener {
 *
 *     &#64;EventListener
 *     public void handleUserRegistered(UserRegisteredEvent event) {
 *         // Email gönder, bildirim oluştur, vb.
 *         sendWelcomeEmail(event.get("email"));
 *     }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PublishEvent {

    /**
     * Event tipi.
     * <p>
     * Yayınlanacak event sınıfı. DomainEvent'dan extend etmelidir.
     * <p>
     * Belirtilmezse, metottan dönen değer bir event ise o yayınlanır.
     *
     * @return Event tipi
     */
    Class<? extends DomainEvent> eventType() default DomainEvent.class;

    /**
     * Event builder metodu adı.
     * <p>
     * Bu metot, metot sonucunu alarak event oluşturur.
     * <p>
     * Örnek: "toEvent" - result.toEvent() çağrılır
     *
     * @return Builder metodu adı
     */
    String eventBuilder() default "";

    /**
     * Asenkron yayınla.
     * <p>
     * true ise, event ayrı bir thread'de yayınlanır.
     *
     * @return Asenkron mu?
     */
    boolean async() default true;

    /**
     * Hata durumunda da yayınla.
     * <p>
     * true ise, metot hata fırlatsa bile event yayınlanır.
     * Hata bilgisi event payload'ına eklenir.
     *
     * @return Hata durumunda da yayınla
     */
    boolean publishOnError() default false;

    /**
     * Event payload'ına metot parametrelerini ekle.
     * <p>
     * true ise, metot parametreleri event payload'ına eklenir.
     *
     * @return Parametreleri ekle
     */
    boolean includeArguments() default false;

    /**
     * Event payload'ına metot sonucunu ekle.
     * <p>
     * true ise, metot sonucu event payload'ına "result" anahtarıyla eklenir.
     *
     * @return Sonucu ekle
     */
    boolean includeResult() default true;
}
