package com.sinay.core.server.fileupload.entities;

import com.sinay.core.server.base.BaseEntity;
import com.sinay.core.server.fileupload.enums.FileCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Sisteme yüklenen bir dosyayı temsil eden entity.
 * Dosya metadatasını veritabanında saklarken, gerçek dosya içeriği diskte saklanır.
 */
@Entity
@Table(name = "uploaded_files", indexes = {
    @Index(name = "idx_owner_user", columnList = "owner_user_id"),
    @Index(name = "idx_category", columnList = "category"),
    @Index(name = "idx_visible", columnList = "visible")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class UploadedFile extends BaseEntity {

    /**
     * Bu dosyayı yükleyen kullanıcıya foreign key referansı.
     */
    @Column(name = "owner_user_id", nullable = false)
    @JdbcTypeCode(SqlTypes.UUID)
    private UUID ownerUserId;

    /**
     * Dosyanın kategorisi (IMAGE, VIDEO, DOCUMENT, ARCHIVE, AUDIO).
     * Doğrulama kurallarını ve işleme davranışını belirler.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private FileCategory category;

    /**
     * Yükleyen tarafından sağlanan orijinal dosya adı.
     * İndirme ve görüntüleme amaçları için korunur.
     */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /**
     * Depolamada kullanılan dosya adı (UUID tabanlı).
     * Benzersizliği sağlar ve çakışmaları önler.
     */
    @Column(name = "stored_filename", nullable = false, length = 255)
    private String storedFilename;

    /**
     * Nokta olmadan dosya uzantısı (örn: "jpg", "pdf").
     * Orijinal dosya adından çıkarılır.
     */
    @Column(name = "extension", nullable = false, length = 20)
    private String extension;

    /**
     * Dosyanın MIME tipi (örn: "image/jpeg", "application/pdf").
     * Dosya içeriğinden algılanır veya yükleyen tarafından sağlanır.
     */
    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    /**
     * Bayt cinsinden dosya boyutu.
     */
    @Column(name = "file_size", nullable = false)
    private Long fileSize;

    /**
     * Dosya bütünlüğü doğrulaması için SHA-256 checksum'i.
     * İsteğe bağlı ama kritik dosyalar için önerilir.
     */
    @Column(name = "checksum", length = 64)
    private String checksum;

    /**
     * Dosyanın diskte saklandığı tam yol.
     * Depolama konfigürasyonuna göre göreli veya mutlak yol.
     */
    @Column(name = "storage_path", nullable = false, length = 500)
    private String storagePath;

    /**
     * Piksel cinsinden resim genişliği.
     * Sadece resim dosyaları için geçerlidir.
     */
    @Column(name = "image_width")
    private Integer imageWidth;

    /**
     * Piksel cinsinden resim yüksekliği.
     * Sadece resim dosyaları için geçerlidir.
     */
    @Column(name = "image_height")
    private Integer imageHeight;

    /**
     * Küçük thumbnail yolu (örn: 150x150).
     * Thumbnail oluşturma etkinleştirildiğinde sadece resim dosyaları için oluşturulur.
     */
    @Column(name = "thumbnail_sm_path", length = 500)
    private String thumbnailSmPath;

    /**
     * Orta thumbnail yolu (örn: 300x300).
     * Thumbnail oluşturma etkinleştirildiğinde sadece resim dosyaları için oluşturulur.
     */
    @Column(name = "thumbnail_md_path", length = 500)
    private String thumbnailMdPath;

    /**
     * Büyük thumbnail yolu (örn: 800x800).
     * Thumbnail oluşturma etkinleştirildiğinde sadece resim dosyaları için oluşturulur.
     */
    @Column(name = "thumbnail_lg_path", length = 500)
    private String thumbnailLgPath;
}
