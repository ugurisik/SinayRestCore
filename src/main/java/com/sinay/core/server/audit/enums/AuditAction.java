package com.sinay.core.server.audit.enums;

/**
 * Audit log için işlem tipi.
 * <p>
 * Bu enum, sistemde gerçekleştirilen çeşitli işlemleri kategorize eder.
 * Her bir audit log kaydı bu tiplerden birine atanır.
 */
public enum AuditAction {

    /**
     * Kullanıcı girişi.
     */
    LOGIN,

    /**
     * Kullanıcı çıkışı.
     */
    LOGOUT,

    /**
     * Yeni kayıt oluşturma.
     */
    CREATE,

    /**
     * Mevcut kaydı güncelleme.
     */
    UPDATE,

    /**
     * Kayıt silme (soft delete).
     */
    DELETE,

    /**
     * Kayıt okuma/görüntüleme.
     */
    READ,

    /**
     * Kullanıcı kayıt olma.
     */
    REGISTER,

    /**
     * Kullanıcı banlama.
     */
    BAN,

    /**
     * Kullanıcı ban kaldırma.
     */
    UNBAN,

    /**
     * Kullanıcı affetme/thaw.
     */
    FORGIVE,

    /**
     * Dosya yükleme.
     */
    FILE_UPLOAD,

    /**
     * Dosya silme.
     */
    FILE_DELETE,

    /**
     * Admin tarafından gerçekleştirilen işlem.
     */
    ADMIN_ACTION,

    /**
     * Diğer tüm işlemler.
     */
    OTHER
}
