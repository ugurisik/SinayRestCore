package com.sinay.core.fileupload.service;

import com.sinay.core.fileupload.config.UploadProperties;
import com.sinay.core.fileupload.enums.FileCategory;
import com.sinay.core.fileupload.exception.FileStorageException;
import com.sinay.core.utils.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Dosya sistemi depolama işlemlerini ele alan servis.
 * <p>
 * Bu servis depolama, okuma ve silme dahil tüm fiziksel dosya depolama işlemlerini yönetir.
 * Dosyaları dosya kategorisi ve tarihe dayalı yapılandırılmış bir dizin hiyerarşisinde düzenler.
 * <p>
 * Dizin Yapısı:
 * <pre>
 * {upload.base-dir}/
 * ├── image/
 * │   └── 2026/03/27/{uuid}/
 * │       ├── {uuid}_original.jpg
 * │       ├── thumb_sm.webp
 * │       ├── thumb_md.webp
 * │       └── thumb_lg.webp
 * ├── video/
 * │   └── 2026/03/27/{uuid}/{file}
 * ├── document/
 * ├── archive/
 * └── audio/
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final UploadProperties uploadProperties;

    /**
     * Tarihe dayalı dizin yolları oluşturmak için tarih formatı (yyyy/MM/dd).
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * Bir multipart dosyayı diskte belirtilen yola depolar.
     * <p>
     * Bu yöntem actual dosya yazma işlemini gerçekleştirir, dosyanın yazılmasından
     * önce hedef dizinin var olduğundan emin olur.
     *
     * @param file Depolanacak multipart dosya
     * @param path Dosyanın depolanacağı göreli yol (base-dir'e göreli)
     * @return Dosyanın depolandığı tam yol
     * @throws FileStorageException Dosya I/O hataları, yetersiz disk alanı
     *                              veya izin sorunları nedeniyle depolanamazsa
     */
    public String storeFile(MultipartFile file, String path) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("Cannot store empty file");
        }

        if (path == null || path.isBlank()) {
            throw new FileStorageException("Storage path cannot be null or empty");
        }

        try {
            // Tam dosya yolunu oluştur
            String fullPath = getFullPath(path);
            File targetFile = new File(fullPath);

            // Üst dizinin var olduğundan emin ol
            createDirectory(targetFile.getParent());

            // Dosyanın zaten var olup olmadığını kontrol et
            if (targetFile.exists()) {
                log.warn("File already exists at path: {}, overwriting", fullPath);
            }

            // Kullanılabilir disk alanını kontrol et
            long requiredSpace = file.getSize();
            File targetDir = targetFile.getParentFile();
            if (targetDir != null && targetDir.getUsableSpace() < requiredSpace) {
                long availableSpace = targetDir.getUsableSpace();
                throw FileStorageException.insufficientDiskSpace(requiredSpace, availableSpace);
            }

            // Apache Commons IO kullanarak dosyayı hedef konuma kopyala
            try (InputStream inputStream = file.getInputStream()) {
                FileUtils.copyInputStreamToFile(inputStream, targetFile);
            }

            log.info("File stored successfully at: {}", fullPath);
            return fullPath;

        } catch (IOException e) {
            log.error("Failed to store file at path: {}", path, e);
            throw FileStorageException.unableToWriteFile(path, e);
        }
    }

    /**
     * Diskten bir dosyayı okur ve File nesnesi olarak döndürür.
     * <p>
     * Bu yöntem dosyanın var olduğunu ve okunabilir olduğunu doğruladıktan sonra
     * çağırana döndürür.
     *
     * @param path Okunacak dosyanın göreli yolu (base-dir'e göreli)
     * @return Diskteki dosyayı temsil eden File nesnesi
     * @throws FileStorageException Dosya mevcut değilse veya okunamıyorsa
     */
    public File readFile(String path) {
        if (path == null || path.isBlank()) {
            throw new FileStorageException("File path cannot be null or empty");
        }

        String fullPath = getFullPath(path);
        File file = new File(fullPath);

        if (!file.exists()) {
            throw new FileStorageException("File not found: " + path);
        }

        if (!file.isFile()) {
            throw new FileStorageException("Path is not a file: " + path);
        }

        if (!file.canRead()) {
            throw new FileStorageException("File cannot be read (permission denied): " + path);
        }

        log.debug("File read successfully from: {}", fullPath);
        return file;
    }

    /**
     * Diskten bir dosyayı bayt dizisi olarak okur.
     * <p>
     * Bu, tüm dosya içeriğini belleğe okuyan bir kolaylık yöntemidir.
     * Büyük dosyalar için streaming yaklaşımlarını kullanmayı düşünün.
     *
     * @param path Okunacak dosyanın göreli yolu (base-dir'e göreli)
     * @return Bayt dizisi olarak dosya içeriği
     * @throws FileStorageException Dosya okunamıyorsa
     */
    public byte[] readFileAsBytes(String path) {
        File file = readFile(path);

        try {
            return FileUtils.readFileToByteArray(file);
        } catch (IOException e) {
            log.error("Failed to read file content from: {}", path, e);
            throw new FileStorageException("Failed to read file content: " + path, e);
        }
    }

    /**
     * Diskten bir dosyayı siler.
     * <p>
     * Bu yöntem dosyayı dosya sisteminden kalıcı olarak kaldırır.
     * Bu işlem geri alınamaz olduğu için dikkatli kullanın.
     *
     * @param path Silinecek dosyanın göreli yolu (base-dir'e göreli)
     * @return Dosya başarıyla silindiyse true, dosya mevcut değilse false
     * @throws FileStorageException Dosya mevcut ama silinemiyorsa
     */
    public boolean deleteFile(String path) {
        if (path == null || path.isBlank()) {
            throw new FileStorageException("File path cannot be null or empty");
        }

        String fullPath = getFullPath(path);
        File file = new File(fullPath);

        if (!file.exists()) {
            log.debug("File does not exist, nothing to delete: {}", fullPath);
            return false;
        }

        try {
            FileUtils.forceDelete(file);
            log.info("File deleted successfully: {}", fullPath);
            return true;
        } catch (IOException e) {
            log.error("Failed to delete file: {}", path, e);
            throw FileStorageException.unableToDeleteFile(path, e);
        }
    }

    /**
     * Bir dizini ve tüm içeriğini yinelemeli olarak siler.
     * <p>
     * Bu yöntem dizini ve içindeki tüm dosyaları kalıcı olarak kaldırır.
     * Bu işlem geri alınamaz olduğu için dikkatli kullanın.
     *
     * @param path Silinecek dizinin göreli yolu (base-dir'e göreli)
     * @return Dizin başarıyla silindiyse true, mevcut değilse false
     * @throws FileStorageException Dizin mevcut ama silinemiyorsa
     */
    public boolean deleteDirectory(String path) {
        if (path == null || path.isBlank()) {
            throw new FileStorageException("Directory path cannot be null or empty");
        }

        String fullPath = getFullPath(path);
        File directory = new File(fullPath);

        if (!directory.exists()) {
            log.debug("Directory does not exist, nothing to delete: {}", fullPath);
            return false;
        }

        if (!directory.isDirectory()) {
            throw new FileStorageException("Path is not a directory: " + path);
        }

        try {
            FileUtils.deleteDirectory(directory);
            log.info("Directory deleted successfully: {}", fullPath);
            return true;
        } catch (IOException e) {
            log.error("Failed to delete directory: {}", path, e);
            throw FileStorageException.unableToDeleteFile(path, e);
        }
    }

    /**
     * Belirtilen yolda bir dizin oluşturur (zaten mevcut değilse).
     * <p>
     * Bu yöntem gerektiğinde tüm üst dizinleri oluşturur.
     * Dizin zaten mevcutsa herhangi bir işlem yapılmaz.
     *
     * @param path Oluşturulacak dizinin göreli yolu (base-dir'e göreli)
     * @throws FileStorageException Dizin izin sorunları veya diğer I/O hataları
     *                              nedeniyle oluşturulamazsa
     */
    public void createDirectory(String path) {
        if (path == null || path.isBlank()) {
            throw new FileStorageException("Directory path cannot be null or empty");
        }

        String fullPath = getFullPath(path);
        File directory = new File(fullPath);

        if (directory.exists()) {
            if (directory.isDirectory()) {
                log.debug("Directory already exists: {}", fullPath);
                return;
            } else {
                throw new FileStorageException(
                    "Path exists but is not a directory: " + path
                );
            }
        }

        try {
            FileUtils.forceMkdir(directory);
            log.debug("Directory created successfully: {}", fullPath);
        } catch (IOException e) {
            log.error("Failed to create directory: {}", path, e);
            throw new FileStorageException("Failed to create directory: " + path, e);
        }
    }

    /**
     * Bir dosya için kategorisine ve benzersiz ID'sine dayalı depolama yolu oluşturur.
     * <p>
     * Yapı: {category}/{yyyy/MM/dd}/{uuid}/
     * Bu, herhangi bir dizinin çok büyük olmasını önleyen dosyaları
     * tarihe dayalı dizinlere dağıtan organize bir hiyerarşi oluşturur.
     * <p>
     * Örnekler:
     * <ul>
     *   <li>image/2026/03/27/550e8400-e29b-41d4-a716-446655440000/</li>
     *   <li>video/2026/03/27/550e8400-e29b-41d4-a716-446655440000/</li>
     *   <li>document/2026/03/27/550e8400-e29b-41d4-a716-446655440000/</li>
     * </ul>
     *
     * @param category Dosya kategorisi (IMAGE, VIDEO, DOCUMENT, vb.)
     * @param fileId Dosya için benzersiz tanımlayıcı
     * @return Göreli depolama yolu (base-dir veya dosya adı içermez)
     */
    public String getStoragePath(FileCategory category, UUID fileId) {
        if (category == null) {
            throw new IllegalArgumentException("File category cannot be null");
        }

        if (fileId == null) {
            throw new IllegalArgumentException("File ID cannot be null");
        }

        // Kategori adını al, Türkçe karakterleri dönüştür ve lowercase yap
        String categoryPath = StringUtils.normalizeTurkishChars(category.name().toLowerCase());
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        String uuidPath = fileId.toString();

        return String.format("%s/%s/%s/", categoryPath, datePath, uuidPath);
    }

    /**
     * Base path ile relative path'i birleştirir ve tam absolute path döndürür.
     * <p>
     * Path duplication önlemek için, eğer relativePath zaten base path ile
     * başlıyorsa, doğrudan normalize edilmiş hali döndürülür.
     *
     * @param relativePath Base path'e eklenecek göreli yol (null veya boş olabilir)
     * @return Tam absolute path
     */
    public String getFullPath(String relativePath) {
        String basePath = uploadProperties.getBaseDir();

        if (relativePath == null || relativePath.isBlank()) {
            return Paths.get(basePath).normalize().toString();
        }

        // Path'i normalize et ve başındaki/sonundaki separator'leri temizle
        String normalizedRelative = relativePath.replace('\\', '/').replaceAll("/+", "/");

        // Eğer relativePath zaten base path ile başlıyorsa, duplication önle
        if (normalizedRelative.startsWith(basePath.replace('\\', '/'))) {
            log.debug("Path zaten base path ile başlıyor, duplication önlendi: {}", relativePath);
            return Paths.get(relativePath).normalize().toString();
        }

        // Değilse, basePath ile birleştir
        Path combinedPath = Paths.get(basePath, normalizedRelative);
        return combinedPath.normalize().toString();
    }

    /**
     * Belirtilen yolda bir dosyanın var olup olmadığını kontrol eder.
     *
     * @param path Kontrol edilecek göreli yol (base-dir'e göreli)
     * @return Dosya varsa true, aksi halde false
     */
    public boolean fileExists(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        String fullPath = getFullPath(path);
        File file = new File(fullPath);
        return file.exists() && file.isFile();
    }

    /**
     * Bir dosyanın boyutunu bayt cinsinden alır.
     *
     * @param path Dosyanın göreli yolu (base-dir'e göreli)
     * @return Bayt cinsinden dosya boyutu
     * @throws FileStorageException Dosya mevcut değilse
     */
    public long getFileSize(String path) {
        File file = readFile(path);
        return file.length();
    }

    /**
     * Depolama dizini için kullanılabilir disk alanını alır.
     *
     * @return Bayt cinsinden kullanılabilir disk alanı
     */
    public long getAvailableDiskSpace() {
        File baseDir = new File(uploadProperties.getBaseDir());
        return baseDir.getUsableSpace();
    }

    /**
     * Depolama dizini için toplam disk alanını alır.
     *
     * @return Bayt cinsinden toplam disk alanı
     */
    public long getTotalDiskSpace() {
        File baseDir = new File(uploadProperties.getBaseDir());
        return baseDir.getTotalSpace();
    }
}
