package com.sinay.core.server.fileupload.enums;

import com.sinay.core.server.exception.UsErrorCode;
import com.sinay.core.server.exception.UsException;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/**
 * Dosya kategorilerini temsil eden enum.
 * Her kategori izin verilen dosya uzantılarını, maksimum dosya boyutunu ve
 * bu tür dosyalar için thumbnail oluşturulup oluşturulmayacağını tanımlar.
 */
public enum FileCategory {

    /**
     * Görsel dosyalar - yaygın resim formatlarını destekler, thumbnail oluşturur
     */
    IMAGE(
        List.of("jpg", "jpeg", "png", "gif", "webp"),
        10,
        true
    ),

    /**
     * Video dosyaları - yaygın video formatlarını destekler, thumbnail oluşturmaz
     */
    VIDEO(
        List.of("mp4", "mov", "webm"),
        100,
        false
    ),

    /**
     * Doküman dosyaları - Ofis ve metin dosyalarını destekler, thumbnail oluşturmaz
     */
    DOCUMENT(
        List.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt"),
        20,
        false
    ),

    /**
     * Arşiv dosyaları - Sıkıştırılmış arşiv formatlarını destekler, thumbnail oluşturmaz
     */
    ARCHIVE(
        List.of("zip", "rar", "7z"),
        50,
        false
    ),

    /**
     * Ses dosyaları - Yaygın ses formatlarını destekler, thumbnail oluşturmaz
     */
    AUDIO(
        List.of("mp3", "wav", "m4a", "aac"),
        30,
        false
    );

    private final List<String> allowedExtensions;
    private final int maxSizeMB;
    private final boolean generateThumbnails;

    // Uzantıdan kategoriye eşleştirme için cache
    private static final Map<String, FileCategory> EXTENSION_MAP = new HashMap<>();

    // MIME tipinden kategoriye eşleştirme için cache
    private static final Map<String, FileCategory> MIME_TYPE_MAP = new HashMap<>();

    static {
        // Uzantı haritasını oluştur
        for (FileCategory category : values()) {
            for (String extension : category.allowedExtensions) {
                EXTENSION_MAP.put(extension.toLowerCase(), category);
            }
        }

        // MIME tip haritasını oluştur
        // Görsel MIME tipleri
        MIME_TYPE_MAP.put("image/jpeg", IMAGE);
        MIME_TYPE_MAP.put("image/jpg", IMAGE);
        MIME_TYPE_MAP.put("image/png", IMAGE);
        MIME_TYPE_MAP.put("image/gif", IMAGE);
        MIME_TYPE_MAP.put("image/webp", IMAGE);

        // Video MIME tipleri
        MIME_TYPE_MAP.put("video/mp4", VIDEO);
        MIME_TYPE_MAP.put("video/quicktime", VIDEO);
        MIME_TYPE_MAP.put("video/webm", VIDEO);

        // Doküman MIME tipleri
        MIME_TYPE_MAP.put("application/pdf", DOCUMENT);
        MIME_TYPE_MAP.put("application/msword", DOCUMENT);
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.wordprocessingml.document", DOCUMENT);
        MIME_TYPE_MAP.put("application/vnd.ms-excel", DOCUMENT);
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", DOCUMENT);
        MIME_TYPE_MAP.put("application/vnd.ms-powerpoint", DOCUMENT);
        MIME_TYPE_MAP.put("application/vnd.openxmlformats-officedocument.presentationml.presentation", DOCUMENT);
        MIME_TYPE_MAP.put("text/plain", DOCUMENT);

        // Arşiv MIME tipleri
        MIME_TYPE_MAP.put("application/zip", ARCHIVE);
        MIME_TYPE_MAP.put("application/x-rar-compressed", ARCHIVE);
        MIME_TYPE_MAP.put("application/x-7z-compressed", ARCHIVE);

        // Ses MIME tipleri
        MIME_TYPE_MAP.put("audio/mpeg", AUDIO);
        MIME_TYPE_MAP.put("audio/mp3", AUDIO);
        MIME_TYPE_MAP.put("audio/wav", AUDIO);
        MIME_TYPE_MAP.put("audio/mp4", AUDIO);
        MIME_TYPE_MAP.put("audio/x-m4a", AUDIO);
        MIME_TYPE_MAP.put("audio/aac", AUDIO);
    }

    /**
     * FileCategory enum kurucusu.
     *
     * @param allowedExtensions Bu kategori için izin verilen dosya uzantıları
     * @param maxSizeMB Maksimum dosya boyutu (megabayt cinsinden)
     * @param generateThumbnails Bu kategori için thumbnail oluşturulup oluşturulmayacağı
     */
    FileCategory(List<String> allowedExtensions, int maxSizeMB, boolean generateThumbnails) {
        this.allowedExtensions = allowedExtensions;
        this.maxSizeMB = maxSizeMB;
        this.generateThumbnails = generateThumbnails;
    }

    /**
     * Bu kategori için izin verilen dosya uzantıları listesini döndürür.
     *
     * @return Değiştirilemez uzantı listesi (noktasız)
     */
    public List<String> getAllowedExtensions() {
        return Collections.unmodifiableList(allowedExtensions);
    }

    /**
     * Bu kategori için maksimum dosya boyutunu döndürür.
     *
     * @return Maksimum boyut (megabayt cinsinden)
     */
    public int getMaxSizeMB() {
        return maxSizeMB;
    }

    /**
     * Bu kategori için maksimum dosya boyutunu bayt cinsinden döndürür.
     *
     * @return Maksimum boyut (bayt cinsinden)
     */
    public long getMaxSizeBytes() {
        return (long) maxSizeMB * 1024 * 1024;
    }

    /**
     * Bu kategori için thumbnail oluşturulup oluşturulmayacağını kontrol eder.
     *
     * @return Thumbnail oluşturulacaksa true, aksi halde false
     */
    public boolean shouldGenerateThumbnails() {
        return generateThumbnails;
    }

    /**
     * Dosya uzantısına göre FileCategory belirler.
     *
     * @param extension Dosya uzantısı (noktalı veya noktasız, büyük/küçük harf duyarsız)
     * @return İlgili FileCategory
     */
    public static FileCategory fromExtension(String extension) {
        if (extension == null || extension.isBlank()) {
            UsException.firlat("Dosya uzantısı null veya boş olamaz", UsErrorCode.INVALID_PARAMETER);
        }

        // Varsa noktayı kaldır ve küçük harfe çevir
        String normalizedExtension = extension.toLowerCase();
        if (normalizedExtension.startsWith(".")) {
            normalizedExtension = normalizedExtension.substring(1);
        }

        FileCategory category = EXTENSION_MAP.get(normalizedExtension);
        if (category == null) {
            UsException.firlat(
                "Desteklenmeyen dosya uzantısı: " + extension +
                ". Desteklenen uzantılar: " + EXTENSION_MAP.keySet(),
                UsErrorCode.UNSUPPORTED_FILE_TYPE
            );
        }

        return category;
    }

    /**
     * MIME tipine göre FileCategory belirler.
     *
     * @param mimeType MIME tipi (büyük/küçük harf duyarsız)
     * @return İlgili FileCategory
     */
    public static FileCategory fromMimeType(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            UsException.firlat("MIME tipi null veya boş olamaz", UsErrorCode.INVALID_PARAMETER);
        }

        String normalizedMimeType = mimeType.toLowerCase().trim();
        FileCategory category = MIME_TYPE_MAP.get(normalizedMimeType);

        if (category == null) {
            UsException.firlat(
                "Desteklenmeyen MIME tipi: " + mimeType +
                ". Desteklenen MIME tipleri: " + MIME_TYPE_MAP.keySet(),
                UsErrorCode.UNSUPPORTED_MEDIA_TYPE
            );
        }

        return category;
    }

    /**
     * Verilen uzantının bu kategori için izin verilip verilmediğini kontrol eder.
     *
     * @param extension Kontrol edilecek dosya uzantısı (noktalı veya noktasız)
     * @return Uzantı izin veriliyorsa true, aksi halde false
     */
    public boolean isExtensionAllowed(String extension) {
        if (extension == null || extension.isBlank()) {
            return false;
        }

        String normalizedExtension = extension.toLowerCase();
        if (normalizedExtension.startsWith(".")) {
            normalizedExtension = normalizedExtension.substring(1);
        }

        return allowedExtensions.contains(normalizedExtension);
    }

    /**
     * Verilen dosya boyutunun bu kategori için izin verilen limit içinde olup olmadığını kontrol eder.
     *
     * @param sizeInBytes Dosya boyutu (bayt cinsinden)
     * @return Boyut limit içindeyse true, aksi halde false
     */
    public boolean isSizeAllowed(long sizeInBytes) {
        return sizeInBytes > 0 && sizeInBytes <= getMaxSizeBytes();
    }
}
