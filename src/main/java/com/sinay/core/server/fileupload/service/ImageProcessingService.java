package com.sinay.core.server.fileupload.service;

import com.sinay.core.server.fileupload.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Thumbnailator kütüphanesini kullanarak resimleri işleyen servis.
 * <p>
 * Thumbnail oluşturma, resim sıkıştırma ve resim metadata çıkarma işlevselliği sağlar.
 * Hem statik resimleri hem de animasyonlu GIF'leri uygun şekilde ele alır.
 * <p>
 * Thumbnail özellikleri:
 * - Small (sm): 160x160 - Profil resimleri, avatarlar
 * - Medium (md): 480x480 - Grid görünümü thumbnail'ları
 * - Large (lg): 1280x1280 - Detay görünümü thumbnail'ları
 * <p>
 * Format: WebP
 * Kalite: 0.85
 * En/Oran: Korunur (sınırlar içinde, kırpma olmadan)
 * <p>
 * GIF İşleme:
 * - Orijinal GIF: Olduğu gibi korunur (animasyon korunur)
 * - Thumbnail'lar: İlk kare çıkarılır ve WebP'e dönüştürülür (statik resim)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageProcessingService {

    private final UploadProperties uploadProperties;

    /**
     * Küçük thumbnail'lar için boyut tanımlayıcısı.
     */
    public static final String SIZE_SMALL = "sm";

    /**
     * Orta thumbnail'lar için boyut tanımlayıcısı.
     */
    public static final String SIZE_MEDIUM = "md";

    /**
     * Büyük thumbnail'lar için boyut tanımlayıcısı.
     */
    public static final String SIZE_LARGE = "lg";

    /**
     * Konfigüre edilmemişse varsayılan thumbnail kalitesi.
     */
    private static final float DEFAULT_QUALITY = 0.85f;

    /**
     * Belirtilen resim için üç thumbnail boyutunu (sm, md, lg) oluşturur.
     * <p>
     * Animasyonlu GIF'ler için, thumbnail'larda sadece ilk kare kullanılır.
     * Tüm thumbnail'lar optimize sıkıştırma için WebP formatına dönüştürülür.
     * <p>
     * Çıktı dosyaları boyut son eklerine sahip isimlerle adlandırılır:
     * - original_sm.webp
     * - original_md.webp
     * - original_lg.webp
     *
     * @param imageFile Kaynak resim dosyası
     * @param outputDir Thumbnail'ların kaydedileceği dizin
     * @return Thumbnail boyutundan dosya yoluna eşleme (örn: {"sm": "/path/to/thumb_sm.webp"})
     * @throws IOException Resim işleme başarısız olursa veya çıktı dizini geçersizse
     */
    public Map<String, String> generateThumbnails(File imageFile, File outputDir) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file must exist");
        }
        if (outputDir == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }

        // Çıktı dizinin var olduğundan emin ol
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + outputDir.getAbsolutePath());
        }

        log.debug("Generating thumbnails for image: {}", imageFile.getName());

        Map<String, String> thumbnailPaths = new HashMap<>();
        UploadProperties.ThumbnailProperty thumbnailConfig = uploadProperties.getThumbnail();
        float quality = (float) thumbnailConfig.getQuality();

        // Konfigürasyondan thumbnail boyutlarını al
        int smallSize = thumbnailConfig.getSizes().getOrDefault("small", 160);
        int mediumSize = thumbnailConfig.getSizes().getOrDefault("medium", 480);
        int largeSize = thumbnailConfig.getSizes().getOrDefault("large", 1280);

        // Uzantı olmadan temel dosya adı oluştur
        String baseName = getBaseFileName(imageFile.getName());

        // Küçük thumbnail oluştur
        File smallThumbnail = new File(outputDir, baseName + "_" + SIZE_SMALL + ".webp");
        createThumbnail(imageFile, smallSize, smallThumbnail);
        thumbnailPaths.put(SIZE_SMALL, smallThumbnail.getAbsolutePath());

        // Orta thumbnail oluştur
        File mediumThumbnail = new File(outputDir, baseName + "_" + SIZE_MEDIUM + ".webp");
        createThumbnail(imageFile, mediumSize, mediumThumbnail);
        thumbnailPaths.put(SIZE_MEDIUM, mediumThumbnail.getAbsolutePath());

        // Büyük thumbnail oluştur
        File largeThumbnail = new File(outputDir, baseName + "_" + SIZE_LARGE + ".webp");
        createThumbnail(imageFile, largeSize, largeThumbnail);
        thumbnailPaths.put(SIZE_LARGE, largeThumbnail.getAbsolutePath());

        log.debug("Generated {} thumbnails for image: {}", thumbnailPaths.size(), imageFile.getName());

        return thumbnailPaths;
    }

    /**
     * Bir resmi belirtilen kalite seviyesine sıkıştırır.
     * <p>
     * Çıktı formatı thumbnail konfigürasyonu tarafından belirlenir (varsayılan: WebP).
     * Orijinal en/oran ve boyutlar korunur.
     * <p>
     * Not: Animasyonlu GIF'ler için, sadece ilk kare sıkıştırılacaktır.
     *
     * @param imageFile Sıkıştırılacak kaynak resim dosyası
     * @param quality   Sıkıştırma kalitesi (0.0 ile 1.0 arası, 1.0 maksimum kalite)
     * @return Sıkıştırılmış dosya (aynı ada ama .webp uzantısıyla kaydedilir)
     * @throws IOException Sıkıştırma başarısız olursa
     */
    public File compressImage(File imageFile, float quality) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file must exist");
        }
        if (quality < 0.0f || quality > 1.0f) {
            throw new IllegalArgumentException("Quality must be between 0.0 and 1.0");
        }

        log.debug("Compressing image: {} with quality: {}", imageFile.getName(), quality);

        // WebP uzantılı çıktı dosyası oluştur
        String baseName = getBaseFileName(imageFile.getName());
        File outputFile = new File(imageFile.getParent(), baseName + "_compressed.webp");

        // Thumbnailator kullanarak resmi sıkıştır
        Thumbnails.of(imageFile)
                .scale(1.0) // Orijinal boyutu koru
                .outputQuality(quality)
                .outputFormat("webp")
                .toFile(outputFile);

        log.debug("Compressed image saved to: {}", outputFile.getAbsolutePath());

        return outputFile;
    }

    /**
     * Bir resim dosyasının boyutlarını (genişlik ve yükseklik) alır.
     * <p>
     * Tüm resmi yüklemeden metadata okumak için Java'nın ImageIO'sunu kullanır.
     * ImageIO tarafından desteklenen tüm formatlarla çalışır (JPEG, PNG, GIF, BMP, WBMP).
     *
     * @param imageFile Analiz edilecek resim dosyası
     * @return int dizisi where [0] genişlik ve [1] yükseklik
     * @throws IOException Dosya okunamıyorsa veya geçerli bir resim değilse
     */
    public int[] getImageDimensions(File imageFile) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file must exist");
        }

        log.debug("Getting dimensions for image: {}", imageFile.getName());

        BufferedImage bufferedImage = ImageIO.read(imageFile);
        if (bufferedImage == null) {
            throw new IOException("Failed to read image file. Unsupported format or corrupted file.");
        }

        int width = bufferedImage.getWidth();
        int height = bufferedImage.getHeight();

        log.debug("Image dimensions: {}x{}", width, height);

        return new int[]{width, height};
    }

    /**
     * Belirtilen boyutta tek bir thumbnail oluşturur.
     * <p>
     * Thumbnail, orijinal en/oranı koruyarak belirtilen boyutlara sığacak şekilde oluşturulur.
     * Hiçbir kırpma yapılmaz.
     * <p>
     * Çıktı formatı optimize sıkıştırma ve kalite için WebP'tir.
     * Kalite yükleme konfigürasyonu tarafından belirlenir (varsayılan: 0.85).
     * <p>
     * Animasyonlu GIF'ler için, sadece ilk kare kullanılır.
     *
     * @param imageFile  Kaynak resim dosyası
     * @param size       Thumbnail'ın maksimum genişlik ve yüksekliği (resim bunun içine sığar)
     * @param outputFile Thumbnail için hedef dosya
     * @throws IOException Thumbnail oluşturma başarısız olursa
     */
    public void createThumbnail(File imageFile, int size, File outputFile) throws IOException {
        if (imageFile == null || !imageFile.exists()) {
            throw new IllegalArgumentException("Image file must exist");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }
        if (outputFile == null) {
            throw new IllegalArgumentException("Output file cannot be null");
        }

        log.debug("Creating {}x{} thumbnail for: {}", size, size, imageFile.getName());

        // Kaliteyi konfigürasyondan al
        float quality = (float) uploadProperties.getThumbnail().getQuality();

        // Üst dizinin var olduğundan emin ol
        File parentDir = outputFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create output directory: " + parentDir.getAbsolutePath());
        }

        // Thumbnailator kullanarak thumbnail oluştur
        Thumbnails.of(imageFile)
                .size(size, size)
                .outputQuality(quality)
                .outputFormat("webp")
                .toFile(outputFile);

        log.debug("Thumbnail created: {}", outputFile.getAbsolutePath());
    }

    /**
     * Tam dosya adından uzantısı olmayan temel dosya adını çıkarır.
     * <p>
     * Örnekler:
     * - "image.jpg" -> "image"
     * - "photo.original.png" -> "photo.original"
     * - "document" -> "document"
     *
     * @param filename Uzantılı tam dosya adı
     * @return Uzantısı olmayan temel dosya adı
     */
    private String getBaseFileName(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "image";
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0 && lastDotIndex < filename.length() - 1) {
            return filename.substring(0, lastDotIndex);
        }

        return filename;
    }
}
