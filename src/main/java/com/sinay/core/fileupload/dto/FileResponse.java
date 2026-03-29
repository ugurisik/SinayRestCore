package com.sinay.core.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sinay.core.fileupload.enums.FileCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Detaylı dosya meta verileri için DTO.
 * UploadedFile entity'sindeki tüm alanları artı kullanıcı adını içerir.
 * Detaylı dosya bilgisi yanıtları için kullanılır.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileResponse {

    /**
     * Dosyanın benzersiz tanımlayıcısı (UUID).
     */
    private UUID id;

    /**
     * Bu dosyayı yükleyen kullanıcının ID'si.
     */
    private UUID ownerUserId;

    /**
     * Bu dosyayı yükleyen kullanıcının kullanıcı adı.
     * Kolaylık için users tablosundan join edilir.
     */
    private String ownerUsername;

    /**
     * Dosyanın kategorisi (GÖRSEL, VİDEO, DÖKÜMAN, ARŞİV, SES).
     */
    private FileCategory category;

    /**
     * Yükleyici tarafından sağlanan orijinal dosya adı.
     */
    private String originalFilename;

    /**
     * UUID tabanlı depolama dosya adı.
     */
    private String storedFilename;

    /**
     * Noktasız dosya uzantısı (örn: "jpg", "pdf").
     */
    private String extension;

    /**
     * Dosyanın MIME tipi (örn: "image/jpeg", "application/pdf").
     */
    private String mimeType;

    /**
     * Dosya boyutu (bayt cinsinden).
     */
    private Long fileSize;

    /**
     * Dosya bütünlük doğrulaması için SHA-256 checksum değeri.
     */
    private String checksum;

    /**
     * Dosyanın diskte depolandığı tam yol.
     */
    private String storagePath;

    /**
     * Görsel genişliği (piksel cinsinden).
     * Sadece görsel dosyaları için geçerlidir.
     */
    private Integer imageWidth;

    /**
     * Görsel yüksekliği (piksel cinsinden).
     * Sadece görsel dosyaları için geçerlidir.
     */
    private Integer imageHeight;

    /**
     * Küçük thumbnail yolu (örn: 160x160).
     */
    private String thumbnailSmPath;

    /**
     * Orta thumbnail yolu (örn: 480x480).
     */
    private String thumbnailMdPath;

    /**
     * Büyük thumbnail yolu (örn: 1280x1280).
     */
    private String thumbnailLgPath;

    /**
     * Dosyanın yüklendiği zaman damgası.
     */
    private LocalDateTime createdAt;

    /**
     * Dosyanın son güncellendiği zaman damgası.
     */
    private LocalDateTime updatedAt;
}
