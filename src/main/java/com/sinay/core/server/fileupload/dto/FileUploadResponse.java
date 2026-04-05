package com.sinay.core.server.fileupload.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sinay.core.server.fileupload.enums.FileCategory;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Başarılı dosya yükleme sonrası döndürülen DTO.
 * Hemen yanıt için gereken temel dosya meta verilerini içerir.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FileUploadResponse {

    /**
     * Yüklenen dosyanın benzersiz tanımlayıcısı (UUID).
     */
    private UUID id;

    /**
     * Yükleyici tarafından sağlanan orijinal dosya adı.
     */
    private String originalFilename;

    /**
     * UUID tabanlı depolama dosya adı.
     */
    private String storedFilename;

    /**
     * Dosyanın kategorisi (GÖRSEL, VİDEO, DÖKÜMAN, ARŞİV, SES).
     */
    private FileCategory category;

    /**
     * Noktasız dosya uzantısı (örn: "jpg", "pdf").
     */
    private String extension;

    /**
     * Dosya boyutu (bayt cinsinden).
     */
    private Long fileSize;

    /**
     * Dosyanın diskte depolandığı tam yol.
     */
    private String storagePath;

    /**
     * Küçük thumbnail yolu (örn: 160x160).
     * Dosya kategorisi için thumbnail oluşturma etkinleştirilmişse sunulur.
     */
    private String thumbnailSmPath;

    /**
     * Orta thumbnail yolu (örn: 480x480).
     * Dosya kategorisi için thumbnail oluşturma etkinleştirilmişse sunulur.
     */
    private String thumbnailMdPath;

    /**
     * Büyük thumbnail yolu (örn: 1280x1280).
     * Dosya kategorisi için thumbnail oluşturma etkinleştirilmişse sunulur.
     */
    private String thumbnailLgPath;

    /**
     * Dosyanın yüklendiği zaman damgası.
     */
    private LocalDateTime createdAt;
}
