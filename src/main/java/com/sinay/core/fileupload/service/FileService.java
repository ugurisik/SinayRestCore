package com.sinay.core.fileupload.service;

import com.sinay.core.fileupload.dto.FileQueryDto;
import com.sinay.core.fileupload.dto.FileResponse;
import com.sinay.core.fileupload.dto.FileUploadResponse;
import com.sinay.core.fileupload.enums.FileCategory;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Dosya yükleme işlemleri için servis arayüzü.
 * <p>
 * Bu servis, dosya yükleme sistemi için ana iş mantığını sağlar,
 * doğrulama, depolama, işleme ve veritabanı işlemlerini koordine eder.
 * <p>
 * Temel sorumluluklar:
 * <ul>
 *   <li>Doğrulama ve işleme ile dosya yükleme</li>
 *   <li>Dosya meta veri alma ve sorgulama</li>
 *   <li>Dosya indirme ve thumbnail sunma</li>
 *   <li>Sahiplik doğrulaması ile dosya silme</li>
 * </ul>
 */
public interface FileService {

    /**
     * Dosyayı sisteme yükler.
     * <p>
     * Bu metod tam dosya yükleme sürecini yönetir:
     * <ol>
     *   <li>Dosya türüne göre kategoriyi otomatik belirler</li>
     *   <li>Dosyayı güvenlik kurallarına göre doğrular</li>
     *   <li>Benzersiz depolama yolu oluşturur</li>
     *   <li>Dosyayı diske kaydeder</li>
     *   <li>Görsel dosyalar için thumbnail oluşturur (etkinleştirilmişse)</li>
     *   <li>Meta veri çıkarır (görseller için boyutlar)</li>
     *   <li>Dosya meta verisini veritabanına kaydeder</li>
     * </ol>
     *
     * @param file   Yüklenecek multipart dosya
     * @param userId Dosyayı yükleyen kullanıcının ID'si
     * @return Yüklenen dosyanın meta verilerini içeren FileUploadResponse
     * @throws com.sinay.core.fileupload.exception.FileValidationException Doğrulama başarısızsa
     * @throws com.sinay.core.fileupload.exception.FileStorageException    Depolama başarısızlıkta
     */
    FileUploadResponse uploadFile(MultipartFile file, UUID userId);

    /**
     * Birden fazla dosyayı sisteme aynı anda yükler.
     * <p>
     * Her dosya bağımsız olarak doğrulanır, depolanır ve işlenir.
     * Doğrulama başarısız olan dosyalar atlanır, başarılı yüklemeler meta verilerini döndürür.
     * <p>
     * Kısmi başarısız olsa bile diğer dosyalar yüklenmeye devam eder.
     *
     * @param files  Yüklenecek multipart dosya listesi
     * @param userId Dosyaları yükleyen kullanıcının ID'si
     * @return Başarıyla yüklenen dosyaların meta verilerini içeren FileUploadResponse listesi
     * @throws com.sinay.core.fileupload.exception.FileValidationException Doğrulama başarısızsa
     * @throws com.sinay.core.fileupload.exception.FileStorageException    Depolama başarısızlıkta
     */
    List<FileUploadResponse> uploadFiles(List<MultipartFile> files, UUID userId);

    /**
     * Belirtilen kullanıcının sahip olduğu dosyaların sayfalanmış listesini alır.
     * <p>
     * Kategoriye göre filtreleme, dosya adına göre arama, sıralama
     * ve sayfalama destekler. Sadece kullanıcıya ait görünür dosyaları döndürür.
     *
     * @param userId   Dosyaları alınacak kullanıcının ID'si
     * @param queryDto Sorgu parametreleri (sayfalama, filtreleme, sıralama)
     * @return Dosya meta verilerini içeren FileResponse nesnelerinin sayfası
     */
    Page<FileResponse> getMyFiles(UUID userId, FileQueryDto queryDto);

    /**
     * Belirli bir dosya için detaylı meta veri alır.
     * <p>
     * Sadece dosya sahibi veya admin dosya meta verilerine erişebilir.
     * Dosya mevcut ve görünür olmalıdır.
     *
     * @param fileId Alınacak dosyanın ID'si
     * @param userId Dosyayı isteyen kullanıcının ID'si (sahiplik kontrolü için)
     * @return Detaylı dosya meta verilerini içeren FileResponse
     * @throws com.sinay.core.fileupload.exception.UploadedFileNotFoundException Dosya bulunamazsa
     * @throws org.springframework.security.access.AccessDeniedException        Kullanıcı sahibi veya admin değilse
     */
    FileResponse getFileById(UUID fileId, UUID userId);

    /**
     * Depolamadan bir dosya indirir.
     * <p>
     * Sadece dosya sahibi veya admin dosyaları indirebilir.
     * Dosya diskte mevcut olmalı ve veritabanında görünür olmalıdır.
     *
     * @param fileId İndirilecek dosyanın ID'si
     * @param userId İndirmeyi isteyen kullanıcının ID'si (sahiplik kontrolü için)
     * @return İndirme için dosyayı temsil eden Resource
     * @throws com.sinay.core.fileupload.exception.UploadedFileNotFoundException Dosya bulunamazsa
     * @throws org.springframework.security.access.AccessDeniedException        Kullanıcı sahibi veya admin değilse
     * @throws com.sinay.core.fileupload.exception.FileStorageException         Diskten dosya okunamazsa
     */
    Resource downloadFile(UUID fileId, UUID userId);

    /**
     * Sistemden bir dosyayı siler.
     * <p>
     * Sadece dosya sahibi veya admin dosyaları silebilir.
     * Veritabanında dosyayı görünmez olarak işaretleyerek soft delete yapar.
     * Fiziksel dosya diskte kalır ancak API üzerinden erişilemez.
     * <p>
     * Kalıcı silme (disk dosyaları dahil) için admin fonksiyonlarını kullanın.
     *
     * @param fileId Silinecek dosyanın ID'si
     * @param userId Silmeyi isteyen kullanıcının ID'si (sahiplik kontrolü için)
     * @throws com.sinay.core.fileupload.exception.UploadedFileNotFoundException Dosya bulunamazsa
     * @throws org.springframework.security.access.AccessDeniedException        Kullanıcı sahibi veya admin değilse
     */
    void deleteFile(UUID fileId, UUID userId);

    /**
     * Bir görsel dosyası için thumbnail alır.
     * <p>
     * Sadece dosya sahibi veya admin thumbnail'ları görüntüleyebilir.
     * Thumbnail'lar üç boyutta oluşturulur:
     * <ul>
     *   <li>sm (small): 160x160</li>
     *   <li>md (medium): 480x480</li>
     *   <li>lg (large): 1280x1280</li>
     * </ul>
     * İstenen thumbnail boyutu mevcut değilse exception fırlatılır.
     *
     * @param fileId Görsel dosyasının ID'si
     * @param size   Thumbnail boyutu (sm, md veya lg)
     * @param userId Thumbnail'ı isteyen kullanıcının ID'si (sahiplik kontrolü için)
     * @return Thumbnail görselini temsil eden Resource
     * @throws com.sinay.core.fileupload.exception.UploadedFileNotFoundException Dosya bulunamazsa
     * @throws org.springframework.security.access.AccessDeniedException        Kullanıcı sahibi veya admin değilse
     * @throws com.sinay.core.fileupload.exception.FileStorageException         Thumbnail okunamazsa
     * @throws IllegalArgumentException                                        Boyut geçersizse
     */
    Resource viewThumbnail(UUID fileId, String size, UUID userId);
}
