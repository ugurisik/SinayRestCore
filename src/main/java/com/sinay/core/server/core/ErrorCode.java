package com.sinay.core.server.core;

/**
 * ObjectCore hata kodları.
 * <p>
 * Result sınıfında hata durumlarını tanımlamak için kullanılır.
 */
public enum ErrorCode {

    /**
     * Kaynak bulunamadı.
     */
    ENTITY_NOT_FOUND("ENTITY_NOT_FOUND", "Kaynak bulunamadı"),

    /**
     * Validasyon hatası.
     */
    VALIDATION_ERROR("VALIDATION_ERROR", "Validasyon hatası"),

    /**
     * Veritabanı hatası.
     */
    DATABASE_ERROR("DATABASE_ERROR", "Veritabanı hatası"),

    /**
     * Reflection hatası (field erişim vb.).
     */
    REFLECTION_ERROR("REFLECTION_ERROR", "Reflection hatası"),

    /**
     * Serileştirme hatası.
     */
    SERIALIZATION_ERROR("SERIALIZATION_ERROR", "Serileştirme hatası"),

    /**
     * Bilinmeyen hata.
     */
    UNKNOWN_ERROR("UNKNOWN_ERROR", "Bilinmeyen hata");

    private final String code;
    private final String message;

    ErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
