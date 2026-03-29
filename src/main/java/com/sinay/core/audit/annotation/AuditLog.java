package com.sinay.core.audit.annotation;

import com.sinay.core.audit.enums.AuditAction;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Audit log tutma anotasyonu.
 * <p>
 * Bu anotasyon ile işaretlenen metodlar çalıştırıldığında, audit log kaydı oluşturulur.
 * Log kaydı şunları içerir:
 * <ul>
 *   <li>Kullanıcı bilgileri (ID, username)</li>
 *   <li>İşlem tipi (action)</li>
 *   <li>Entity tipi ve ID'si</li>
 *   <li>IP adresi ve User Agent</li>
 *   <li>İşlem sonucu (başarılı/başarısız)</li>
 *   <li>Değişiklik bilgisi (opsiyonel)</li>
 * </ul>
 * <p>
 * Kullanım:
 * <pre>
 * &#64;AuditLog(action = AuditAction.CREATE, entityType = "USER")
 * public UserResponse create(CreateRequest request) {
 *     // Metot çalıştırıldıktan sonra audit log kaydı oluşturulur
 * }
 *
 * &#64;AuditLog(action = AuditAction.DELETE, entityType = "FILE", entityId = "#id")
 * public void deleteFile(UUID id) {
 *     // id parametresi entityId olarak kaydedilir
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {

    /**
     * İşlem tipi.
     * <p>
     * Audit kaydının hangi kategoriye ait olduğunu belirtir.
     *
     * @return İşlem tipi
     */
    AuditAction action() default AuditAction.OTHER;

    /**
     * Entity tipi.
     * <p>
     * İşlemin hangi entity üzerinde yapıldığını belirtir (örn: "USER", "FILE", "ORDER").
     *
     * @return Entity tipi
     */
    String entityType() default "";

    /**
     * Entity ID'si (SpEL expression).
     * <p>
     * İşlemin yapıldığı kaydın ID'sini belirtir.
     * <p>
     * Örnekler:
     * <ul>
     *   <li>"#id" - id parametresi</li>
     *   <li>"#request.id" - request parametresinin id'si</li>
     *   <li>"#result.id" - Dönüş değerinin id'si</li>
     * </ul>
     *
     * @return Entity ID expression'ı
     */
    String entityId() default "";

    /**
     * Kullanıcı ID'sini logla.
     * <p>
     * true ise, şu anki kullanıcının ID'si log'a eklenir.
     *
     * @return Kullanıcı ID loglansın mı
     */
    boolean includeUserId() default true;

    /**
     * IP adresini logla.
     * <p>
     * true ise, isteğin yapıldığı IP adresi log'a eklenir.
     *
     * @return IP adresi loglansın mı
     */
    boolean includeIpAddress() default true;

    /**
     * User Agent'ı logla.
     * <p>
     * true ise, isteğin yapıldığı client'in User Agent'ı log'a eklenir.
     *
     * @return User Agent loglansın mı
     */
    boolean includeUserAgent() default true;

    /**
     * Log mesajı.
     * <p>
     * Ek açıklama bilgisi için kullanılır.
     *
     * @return Log mesajı
     */
    String message() default "";
}
