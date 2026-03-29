package com.sinay.core.fileupload.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dosya yükleme sistemi için konfigürasyon özellikleri.
 * <p>
 * Bu sınıf application.yml dosyasından "upload" prefix'i ile konfigürasyon yükler.
 * Dosya işlemenin tüm aspectleri için merkezi konfigürasyon sağlar:
 * kategoriye özgü ayarlar, thumbnail oluşturma, güvenlik kuralları ve dosya adlandırma.
 * <p>
 * application.yml'de örnek konfigürasyon:
 * <pre>
 * upload:
 *   base-dir: "path/to/uploads"
 *   categories:
 *     image:
 *       enabled: true
 *       max-size-mb: 10
 *       extensions: [jpg, jpeg, png, gif, webp]
 *       generate-thumbnails: true
 *   thumbnail:
 *     sizes:
 *       small: 160
 *       medium: 480
 *       large: 1280
 *     quality: 0.85
 *     format: webp
 *   security:
 *     max-filename-length: 255
 *     check-magic-numbers: true
 *     blocked-extensions: [exe, bat, sh, cmd, com, pif]
 *   naming:
 *     format: "{uuid}_{sanitized}"
 * </pre>
 */
@Data
@Validated
@Component
@ConfigurationProperties(prefix = "upload")
public class UploadProperties {

    /**
     * Yüklenen dosyalar için temel dizin.
     * Mutlak yol veya uygulama çalışma dizinine göreli yol olabilir.
     */
    @NotBlank(message = "Upload base directory cannot be blank")
    private String baseDir = "uploads";

    /**
     * Kategoriye özgü konfigürasyonlar.
     * Kategori adlarını (image, video, document, archive, audio) ayarlarına eşler.
     */
    @NotNull(message = "Categories configuration cannot be null")
    @Valid
    private Map<String, CategoryProperty> categories = new HashMap<>();

    /**
     * Thumbnail oluşturma ayarları.
     */
    @NotNull(message = "Thumbnail configuration cannot be null")
    @Valid
    private ThumbnailProperty thumbnail = new ThumbnailProperty();

    /**
     * Dosya yükleme için güvenlikle ilgili ayarlar.
     */
    @NotNull(message = "Security configuration cannot be null")
    @Valid
    private SecurityProperty security = new SecurityProperty();

    /**
     * Dosya adlandırma stratejisi konfigürasyonu.
     */
    @NotNull(message = "Naming configuration cannot be null")
    @Valid
    private NamingProperty naming = new NamingProperty();

    /**
     * Bireysel dosya kategorileri için konfigürasyon özellikleri.
     * <p>
     * Her kategori bağımsız olarak etkinleştirilebilir/devre dışı bırakılabilir ve
     * kendi boyut limitine, izin verilen uzantılara ve thumbnail oluşturma ayarlarına sahiptir.
     */
    @Data
    public static class CategoryProperty {

        /**
         * Bu kategorinin yüklemeler için etkin olup olmadığı.
         */
        private boolean enabled = true;

        /**
         * Bu kategori için maksimum dosya boyutu (megabayt cinsinden).
         */
        @Min(value = 1, message = "Max size must be at least 1 MB")
        @Max(value = 1024, message = "Max size cannot exceed 1024 MB (1 GB)")
        private int maxSizeMb = 10;

        /**
         * Bu kategori için izin verilen dosya uzantıları listesi (başındaki nokta olmadan).
         */
        @NotEmpty(message = "Extensions list cannot be empty")
        private List<@NotBlank(message = "Extension cannot be blank") String> extensions = new ArrayList<>();

        /**
         * Bu kategorideki dosyalar için thumbnail oluşturulup oluşturulmayacağı.
         * Sadece resim türleri için geçerlidir.
         */
        private boolean generateThumbnails = false;

        /**
         * Maksimum dosya boyutunu bayt cinsinden döndürür.
         *
         * @return Maksimum boyut (bayt cinsinden)
         */
        public long getMaxSizeBytes() {
            return (long) maxSizeMb * 1024 * 1024;
        }
    }

    /**
     * Thumbnail oluşturma konfigürasyonu.
     * <p>
     * Oluşturulan thumbnail'ların boyutunu, kalitesini ve formatını kontrol eder.
     * Thumbnail'lar sadece etkinleştirildiğinde ve sadece resim dosyaları için oluşturulur.
     */
    @Data
    public static class ThumbnailProperty {

        /**
         * Thumbnail boyut tanımları.
         * Boyut adlarını (small, medium, large) piksel boyutlarına eşler.
         */
        @NotNull(message = "Thumbnail sizes cannot be null")
        private Map<String, @Min(value = 32, message = "Thumbnail size must be at least 32px") Integer> sizes = new HashMap<>();

        /**
         * Oluşturulan thumbnail'ların kalitesi (0.0 ile 1.0 arası).
         * Daha yüksek değerler daha iyi kalite ama daha büyük dosya boyutu sağlar.
         */
        @DecimalMin(value = "0.1", message = "Quality must be at least 0.1")
        @DecimalMax(value = "1.0", message = "Quality cannot exceed 1.0")
        private double quality = 0.85;

        /**
         * Oluşturulan thumbnail'lar için çıktı formatı.
         * Yaygın formatlar: jpg, png, webp
         */
        @Pattern(regexp = "jpg|jpeg|png|webp", message = "Format must be jpg, jpeg, png, or webp")
        private String format = "webp";

        /**
         * Standart thumbnail boyutlarını başlatan varsayılan constructor.
         */
        public ThumbnailProperty() {
            sizes.put("small", 160);
            sizes.put("medium", 480);
            sizes.put("large", 1280);
        }
    }

    /**
     * Dosya yüklemeleri için güvenlik konfigürasyonu.
     * <p>
     * Kötü niyetli dosya yüklemelerini önlemek için güvenlik kuralları tanımlar:
     * dosya adı doğrulama, magic number doğrulama ve uzantı engelleme.
     */
    @Data
    public static class SecurityProperty {

        /**
         * Yüklenen dosya adları için maksimum izin verilen uzunluk (uzantı hariç).
         * Bu, sorunlara neden olabilecek aşırı uzun dosya adlarını önler.
         */
        @Min(value = 10, message = "Max filename length must be at least 10")
        @Max(value = 255, message = "Max filename length cannot exceed 255")
        private int maxFilenameLength = 255;

        /**
         * Dosya türlerini magic numbers (dosya imzaları) kullanarak doğrulayıp doğrulamama.
         * Etkinleştirildiğinde, gerçek dosya içeriği beyan edilen uzantı ile karşılaştırılır.
         */
        private boolean checkMagicNumbers = true;

        /**
         * Asla izin verilmeyen engellenmiş dosya uzantıları listesi.
         * Genellikle çalıştırılabilir ve script dosyalarını içerir.
         */
        private List<@NotBlank(message = "Blocked extension cannot be blank") String> blockedExtensions = new ArrayList<>();

        /**
         * Varsayılan engellenmiş uzantıları başlatan varsayılan constructor.
         */
        public SecurityProperty() {
            blockedExtensions.add("exe");
            blockedExtensions.add("bat");
            blockedExtensions.add("sh");
            blockedExtensions.add("cmd");
            blockedExtensions.add("com");
            blockedExtensions.add("pif");
            blockedExtensions.add("scr");
            blockedExtensions.add("vbs");
            blockedExtensions.add("js");
            blockedExtensions.add("jar");
        }
    }

    /**
     * Dosya adlandırma stratejisi konfigürasyonu.
     * <p>
     * Çakışmaları önlemek ve güvenli, öngörülebilir dosya adları sağlamak için
     * yüklenen dosyaların nasıl adlandırılacağını tanımlar.
     */
    @Data
    public static class NamingProperty {

        /**
         * Adlandırma format şablonu.
         * Desteklenen yer tutucular:
         * - {uuid}: Rastgele UUID
         * - {timestamp}: Milisaniye cinsinden geçerli zaman damgası
         * - {date}: yyyy-MM-dd formatında geçerli tarih
         * - {original}: Orijinal dosya adı (sanitize edilmiş)
         * - {sanitized}: Uzantısı olmayan sanitize edilmiş orijinal dosya adı
         * <p>
         * Örnekler:
         * - "{uuid}_{sanitized}" -> "550e8400-e29b-41d4-a716-446655440000_myphoto"
         * - "{timestamp}_{original}" -> "1712345678900_original.jpg"
         * - "{date}/{uuid}" -> "2026-03-27/550e8400-e29b-41d4-a716-446655440000"
         */
        @NotBlank(message = "Naming format cannot be blank")
        @Pattern(regexp = "^(\\{uuid\\}|\\{timestamp\\}|\\{date\\}|\\{original\\}|\\{sanitized\\}|[a-zA-Z0-9_\\-/])+$",
                message = "Naming format contains invalid characters")
        private String format = "{uuid}_{sanitized}";

        /**
         * Orijinal dosya uzantısının korunup korunmayacağı.
         * True olduğunda, orijinal uzantı yeni dosya adına eklenir.
         */
        private boolean preserveExtension = true;

        /**
         * Oluşturulan dosya adları için maksimum uzunluk (uzantı hariç).
         * Oluşturulan ad bu uzunluğu aşarsa, kesilecektir.
         */
        @Min(value = 10, message = "Max name length must be at least 10")
        @Max(value = 255, message = "Max name length cannot exceed 255")
        private int maxNameLength = 100;
    }
}
