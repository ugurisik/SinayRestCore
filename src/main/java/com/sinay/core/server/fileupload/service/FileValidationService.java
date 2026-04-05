package com.sinay.core.server.fileupload.service;

import com.sinay.core.server.fileupload.config.UploadProperties;
import com.sinay.core.server.fileupload.enums.FileCategory;
import com.sinay.core.server.fileupload.exception.FileValidationException;
import com.sinay.core.server.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Güvenlik kurallarına ve iş kısıtlamalarına karşı yüklenen dosyaları doğrulayan servis.
 * <p>
 * Bu servis kapsamlı güvenlik doğrulaması gerçekleştirir:
 * <ul>
 *   <li>Tehlikeli uzantilara karşı uzantı beyaz listesi doğrulaması</li>
 *   <li>Dosya içeriğinin uzantıyla eşleştiğinden emin olmak için magic number doğrulaması</li>
 *   <li>Türkçe karakter normalizasyonu ile dosya adı dezenleme</li>
 *   <li>"photo.jpg.exe" tarzı saldırıları önlemek için çift uzantı algılama</li>
 *   <li>Dizin gezinti girişimlerini engellemek için path traversal algılama</li>
 *   <li>Kategoriye özgü limitlere karşı dosya boyutu doğrulaması</li>
 *   <li>MIME tipi doğrulaması</li>
 * </ul>
 * <p>
 * Tüm doğrulama yöntemleri, doğrulama başarısız olduğunda açıklayıcı mesajlarla
 * {@link FileValidationException} fırlatır, böylece çağıran kod kullanıcıların anlamlı geri bildirim sağlayabilir.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileValidationService {

    private final UploadProperties uploadProperties;

    // Güvenlik doğrulaması için desenler
    private static final Pattern SPECIAL_CHAR_PATTERN = Pattern.compile("[^a-zA-Z0-9._-]");

    // UploadProperties'teki varsayılanların ek olarak ek engellenmiş uzantılar
    private static final Set<String> ADDITIONAL_BLOCKED_EXTENSIONS = Set.of(
        "jsp", "jspx", "php", "php3", "php4", "php5", "phtml",
        "asp", "aspx", "cer", "asa", "dll", "sys",
        "cpl", "msi", "msp", "mst", "reg", "vb", "vbscript",
        "wsf", "wsc", "ws", "ps1", "ps1xml", "ps2", "ps2xml",
        "psc1", "psc2", "msh", "msh1", "msh2", "mshxml", "msh1xml",
        "msh2xml", "scf", "lnk", "inf", "ins", "inx", "isu", "job",
        "jse", "js", "vbs", "vbe",  "wsh", "msc", "spl", "cmd"
    );

    // Yaygın dosya türleri için magic number imzaları
    private static final Map<String, byte[]> MAGIC_NUMBERS = Map.ofEntries(
        Map.entry("jpg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        Map.entry("jpeg", new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}),
        Map.entry("png", new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}),
        Map.entry("gif", new byte[]{0x47, 0x49, 0x46, 0x38}),
        Map.entry("webp", new byte[]{0x52, 0x49, 0x46, 0x46}), // RIFF, offset 8-11'de WEBP kontrol edilmesi gerekir
        Map.entry("pdf", new byte[]{0x25, 0x50, 0x44, 0x46}), // %PDF
        Map.entry("mp4", new byte[]{0x66, 0x74, 0x79, 0x70}), // ftyp
        Map.entry("zip", new byte[]{0x50, 0x4B, 0x03, 0x04}),
        Map.entry("mp3", new byte[]{0x49, 0x44, 0x33}) // ID3
    );

    /**
     * Yüklenen bir dosya üzerinde kapsamlı doğrulama gerçekleştirir.
     * <p>
     * Bu, tüm güvenlik kontrollerini koordine eden ana doğrulama yöntemidir:
     * <ol>
     *   <li>Dosyanın boş olmadığını doğrular</li>
     *   <li>Dosya adını sanitize eder ve doğrular</li>
     *   <li>Path traversal girişimlerini kontrol eder</li>
     *   <li>Çift uzantıları kontrol eder</li>
     *   <li>Dosya uzantısını kategori beyaz listesine karşı doğrular</li>
     *   <li>Uzantıları engellenmiş uzantılar listesine karşı kontrol eder</li>
     *   <li>MIME tipinin kategoriyle eşleştiğini doğrular</li>
     *   <li>Dosya boyutunu kategori limitlerine karşı doğrular</li>
     *   <li>Magic number'ın uzantıyla eşleştiğini doğrular (etkinleştirilirse)</li>
     * </ol>
     *
     * @param file     Doğrulanacak multipart dosya
     * @param category Doğrulama kuralları için dosya kategorisi
     * @throws FileValidationException Herhangi bir doğrulama başarısız olursa
     */
    public void validateFile(MultipartFile file, FileCategory category) {
        log.debug("Validating file '{}' for category '{}'", file.getOriginalFilename(), category);

        // Dosyanın boş olup olmadığını kontrol et
        if (file == null || file.isEmpty()) {
            throw FileValidationException.emptyFile();
        }

        // Orijinal dosya adını al ve doğrula
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new FileValidationException("Filename cannot be null or empty");
        }

        // Dosya adını sanitize et
        String sanitizedFilename = sanitizeFilename(originalFilename);
        log.debug("Sanitized filename: '{}' -> '{}'", originalFilename, sanitizedFilename);

        // Path traversal girişimlerini kontrol et
        if (StringUtils.hasPathTraversal(originalFilename)) {
            throw new FileValidationException("Path traversal detected in filename: " + originalFilename);
        }

        // Çift uzantıları kontrol et
        if (StringUtils.hasDoubleExtension(originalFilename)) {
            throw new FileValidationException("Tehlikeli çift uzantı tespit edildi: " + originalFilename);
        }

        // Uzantıyı çıkar
        String extension = StringUtils.getFileExtension(originalFilename);
        log.debug("File extension: '{}'", extension);

        // Uzantıyı doğrula
        validateExtension(extension, category);

        // MIME tipini doğrula
        String mimeType = file.getContentType();
        if (mimeType != null) {
            validateMimeType(mimeType, category);
        }

        // Dosya boyutunu doğrula
        validateFileSize(file.getSize(), category);

        // Magic number'ı doğrula (etkinleştirilmişse)
        if (uploadProperties.getSecurity().isCheckMagicNumbers()) {
            validateMagicNumber(file, category);
        }

        log.debug("File '{}' passed all validations", originalFilename);
    }

    /**
     * Bir dosya uzantısının belirtilen kategori için izin verilip verilmediğini doğrular.
     * <p>
     * Tehlikeli dosya türlerini önlemek için hem kategorinin izin verilen uzantılarına
     * hem de global engellenmiş uzantılar listesine karşı kontrol eder.
     *
     * @param extension Doğrulanacak dosya uzantısı (başındaki nokta olmadan)
     * @param category  Karşılaştırılacak dosya kategorisi
     * @throws FileValidationException Uzantı izin verilmiyorsa veya engellenmişse
     */
    public void validateExtension(String extension, FileCategory category) {
        log.debug("Validating extension '{}' for category '{}'", extension, category);

        // Uzantıyı normalize et (küçük harf, başında nokta yok)
        String normalizedExtension = extension.toLowerCase();
        if (normalizedExtension.startsWith(".")) {
            normalizedExtension = normalizedExtension.substring(1);
        }

        // Uzantının boş olup olmadığını kontrol et
        if (normalizedExtension.isBlank()) {
            throw new FileValidationException("File extension cannot be empty");
        }

        // Kategori izin verilen uzantılara karşı kontrol et
        if (!category.isExtensionAllowed(normalizedExtension)) {
            String allowedExtensions = String.join(", ", category.getAllowedExtensions());
            throw FileValidationException.invalidFileType(
                normalizedExtension,
                allowedExtensions
            );
        }

        // Global engellenmiş uzantılara karşı kontrol et
        List<String> blockedExtensions = uploadProperties.getSecurity().getBlockedExtensions();
        if (blockedExtensions != null && blockedExtensions.contains(normalizedExtension)) {
            throw new FileValidationException(
                String.format("File extension '%s' is blocked for security reasons", normalizedExtension)
            );
        }

        // Ek hardcoded engellenmiş uzantılara karşı kontrol et
        if (ADDITIONAL_BLOCKED_EXTENSIONS.contains(normalizedExtension)) {
            throw new FileValidationException(
                String.format("File extension '%s' is blocked for security reasons", normalizedExtension)
            );
        }

        log.debug("Extension '{}' is valid for category '{}'", normalizedExtension, category);
    }

    /**
     * Bir MIME tipinin belirtilen kategori için uygun olup olmadığını doğrular.
     * <p>
     * Bu doğrulama, bir dosyanın beyan edilen MIME tipinin beklenen kategoriyle eşleşmediği
     * durumları tespit etmeye yardımcı olur, bu da kötü niyetli içerik olduğunu gösterebilir.
     *
     * @param mimeType Doğrulanacak MIME tipi
     * @param category Beklenen dosya kategorisi
     * @throws FileValidationException MIME tipi kategoriyle eşleşmiyorsa
     */
    public void validateMimeType(String mimeType, FileCategory category) {
        log.debug("Validating MIME type '{}' for category '{}'", mimeType, category);

        if (mimeType == null || mimeType.isBlank()) {
            log.warn("MIME type is null or blank, skipping validation");
            return;
        }

        try {
            FileCategory categoryFromMimeType = FileCategory.fromMimeType(mimeType);
            if (categoryFromMimeType != category) {
                throw new FileValidationException(
                    String.format(
                        "MIME type '%s' indicates category '%s' but expected category '%s'",
                        mimeType, categoryFromMimeType, category
                    )
                );
            }
        } catch (IllegalArgumentException e) {
            log.warn("Unknown MIME type '{}', skipping validation: {}", mimeType, e.getMessage());
        }

        log.debug("MIME type '{}' is valid for category '{}'", mimeType, category);
    }

    /**
     * Bir dosyanın magic number'ını (dosya imzasını) uzantısıyla eşleştiğini doğrular.
     * <p>
     * Magic numbers, dosyanın biçimini tanımlayan ilk birkaç bayttır.
     * Bu kontrol, güvenli uzantılarla yeniden adlandırılmış kötü niyetli dosyaları önler.
     * <p>
     * Örneğin, aslında çalışan kod içeren "photo.jpg" adlı bir dosya bu doğrulamayı
     * geçmez çünkü magic number'ı JPEG formatıyla eşleşmez.
     * <p>
     * Bu implementasyon, büyük dosyalarla (örneğin 100 MB videolar) bellek sorunlarını önlemek
     * için sadece gerekli baytları okumak için InputStream kullanır.
     *
     * @param file     Doğrulanacak dosya
     * @param category Beklenen dosya kategorisi
     * @throws FileValidationException Magic number beklenen formatla eşleşmiyorsa
     */
    public void validateMagicNumber(MultipartFile file, FileCategory category) {
        log.debug("Validating magic number for file '{}' in category '{}'", file.getOriginalFilename(), category);

        String extension = StringUtils.getFileExtension(file.getOriginalFilename());
        byte[] expectedMagic = MAGIC_NUMBERS.get(extension.toLowerCase());

        if (expectedMagic == null) {
            log.debug("No magic number defined for extension '{}', skipping check", extension);
            return;
        }

        try (InputStream is = file.getInputStream()) {
            // Magic number doğrulaması için gereken baytları oku
            byte[] fileBytes = new byte[expectedMagic.length];
            int bytesRead = is.read(fileBytes);

            if (bytesRead < expectedMagic.length) {
                throw new FileValidationException(
                    "File is too small to validate magic number"
                );
            }

            // Dosyanın beklenen magic number ile başlayıp başlamadığını kontrol et
            if (!startsWith(fileBytes, expectedMagic)) {
                throw new FileValidationException(
                    String.format(
                        "File content doesn't match expected format for extension '%s'. " +
                        "The file may be corrupted or renamed maliciously.",
                        extension
                    )
                );
            }

            // WebP için özel kontrol (RIFF...WEBP doğrulaması gerekir)
            if ("webp".equalsIgnoreCase(extension)) {
                // WebP için toplam 12 bayt gerekir: 4 için RIFF + 4 için boyut + 4 için WEBP
                // Zaten 4 bayt okuduk, 8 tane daha okumamız gerekiyor
                byte[] webpRemaining = new byte[8];
                int additionalBytesRead = is.read(webpRemaining);

                if (additionalBytesRead < 8) {
                    throw new FileValidationException(
                        "WebP file is too small to be valid"
                    );
                }

                // Offset 8-11'de (dosyada bayt 8-11) WEBP kontrol et
                byte[] webpSignature = {0x57, 0x45, 0x42, 0x50}; // WEBP
                if (!startsWith(new byte[]{webpRemaining[4], webpRemaining[5],
                                          webpRemaining[6], webpRemaining[7]},
                               webpSignature)) {
                    throw new FileValidationException(
                        "RIFF file doesn't contain WebP signature"
                    );
                }
            }

            // MP3 için özel kontrol (birden çok olası format)
            if ("mp3".equalsIgnoreCase(extension)) {
                boolean isValidMp3 = startsWith(fileBytes, new byte[]{0x49, 0x44, 0x33}); // ID3
                if (!isValidMp3) {
                    // MPEG audio header'ı kontrol et (ilk 2 bayt)
                    // İlk 3 baytı zaten fileBytes'de (ID3 için) var
                    // MPEG için, ilk 2 baytın MPEG sync ile eşleşip eşleşmediğini kontrol etmeliyiz
                    if (fileBytes[0] == (byte) 0xFF &&
                        (fileBytes[1] == (byte) 0xFB ||
                         fileBytes[1] == (byte) 0xFA ||
                         fileBytes[1] == (byte) 0xF3)) {
                        isValidMp3 = true;
                    }
                }
                if (!isValidMp3) {
                    throw new FileValidationException(
                        "File doesn't contain valid MP3 signature"
                    );
                }
            }

            log.debug("Magic number validation passed for extension '{}'", extension);

        } catch (IOException e) {
            log.error("Failed to read file for magic number validation", e);
            throw new FileValidationException(
                "Failed to validate file content: " + e.getMessage()
            );
        }
    }

    /**
     * Bir dosyanın boyutunun kategori için izin verilen limit içinde olup olmadığını doğrular.
     *
     * @param fileSize Bayt cinsinden dosya boyutu
     * @param category Karşılaştırılacak dosya kategorisi
     * @throws FileValidationException Dosya boyutu kategori limitini aşıyorsa veya sıfırsa
     */
    public void validateFileSize(long fileSize, FileCategory category) {
        log.debug("Validating file size {} bytes for category '{}'", fileSize, category);

        if (fileSize <= 0) {
            throw FileValidationException.emptyFile();
        }

        long maxSizeBytes = category.getMaxSizeBytes();
        if (fileSize > maxSizeBytes) {
            throw FileValidationException.fileTooLarge(fileSize, maxSizeBytes);
        }

        log.debug("File size {} bytes is within limit {} bytes", fileSize, maxSizeBytes);
    }

    /**
     * Tehlikeli karakterleri kaldırarak ve Türkçe karakterleri normalize ederek bir dosya adını temizler.
     * <p>
     * Aşağıdaki dönüşümleri gerçekleştirir:
     * <ul>
     *   <li>Türkçe karakterleri normalize eder (ç->c, ş->s, ı->i, ğ->g, ö->o, ü->u)</li>
     *   <li>Özel karakterleri kaldırır (alfanümerik, nokta, tire, alt çizgi tutar)</li>
     *   <li>Birden fazla boşlukları tek boşlukla değiştirir</li>
     *   <li>Uçlardaki boşlukları keser</li>
     * </ul>
     * Örnek: "Şükrü'ın Resimleri.jpg" -> "Sukrun_Resimleri.jpg"
     *
     * @param filename Temizlenecek dosya adı
     * @return Temizlenmiş dosya adı
     */
    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return filename;
        }

        log.debug("Sanitizing filename: '{}'", filename);

        // Uzantıyı çıkar
        String extension = "";
        String nameWithoutExtension = filename;
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex > 0) {
            extension = filename.substring(lastDotIndex);
            nameWithoutExtension = filename.substring(0, lastDotIndex);
        }

        // NFD'ye normalize et (karakterleri ayrıştır) ve diyakritikleri kaldır
        String normalized = Normalizer.normalize(nameWithoutExtension, Normalizer.Form.NFD);

        // Diyakritik işaretleri (Unicode birleştirme karakterleri) kaldır
        normalized = normalized.replaceAll("\\p{M}", "");

        // Türkçe karakterleri İngilizce karşılıklarına dönüştür
        normalized = StringUtils.normalizeTurkishChars(normalized);

        // Özel karakterleri kaldır (sadece alfanümerik, nokta, tire, alt çizgi, boşluk tut)
        normalized = SPECIAL_CHAR_PATTERN.matcher(normalized).replaceAll("_");

        // Birden fazla alt çizgiyi tek alt çizgiyle değiştir
        normalized = normalized.replaceAll("_+", "_");

        // Birden fazla boşluğu tek boşlukla değiştir
        normalized = normalized.replaceAll("\\s+", " ");

        // Uçlardaki boşlukları ve alt çizgileri kes
        normalized = normalized.trim().replaceAll("^_+|_+$", "");

        // Maksimum uzunluğu kontrol et
        int maxLength = uploadProperties.getSecurity().getMaxFilenameLength();
        if (normalized.length() > maxLength) {
            normalized = normalized.substring(0, maxLength);
            log.warn("Sanitized filename exceeded max length, truncated to '{}'", normalized);
        }

        // Uzantıyı tekrar ekle
        String result = normalized + extension;

        log.debug("Sanitized result: '{}'", result);
        return result;
    }

    /**
     * Bir bayt dizisinin belirtilen önek ile başlayıp başlamadığını kontrol eder.
     *
     * @param array  Kontrol edilecek bayt dizisi
     * @param prefix Beklenen önek
     * @return Dizi önek ile başlıyorsa true, aksi halde false
     */
    private boolean startsWith(byte[] array, byte[] prefix) {
        if (array == null || prefix == null || array.length < prefix.length) {
            return false;
        }

        for (int i = 0; i < prefix.length; i++) {
            if (array[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }
}
