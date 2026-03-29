package com.sinay.core.fileupload.controller;

import com.sinay.core.dto.response.ApiResponse;
import com.sinay.core.fileupload.dto.FileQueryDto;
import com.sinay.core.fileupload.dto.FileResponse;
import com.sinay.core.fileupload.dto.FileUploadResponse;
import com.sinay.core.fileupload.enums.FileCategory;
import com.sinay.core.fileupload.service.FileService;
import com.sinay.core.ratelimit.annotation.RateLimit;
import com.sinay.core.ratelimit.model.KeyType;
import com.sinay.core.security.userdetails.AppUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Dosya yükleme işlemleri için REST controller.
 * <p>
 * Şu endpoint'leri sağlar:
 * <ul>
 *   <li>Dosya yükleme (doğrulama ve işleme ile)</li>
 *   <li>Dosya meta veri alma ve sorgulama</li>
 *   <li>Dosya indirme ve thumbnail sunma</li>
 *   <li>Sahiplik doğrulaması ile dosya silme</li>
 * </ul>
 * <p>
 * Tüm endpoint'ler kimlik doğrulama gerektirir ve kötüye kullanımı önlemek için rate limit'lidir.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    /**
     * POST /api/v1/files/upload
     * Sisteme tek bir dosya yükler.
     * <p>
     * Dosya güvenlik kuralları ve kategori kısıtlamalarına göre doğrulanır,
     * diske depolanır ve meta veri veritabanına kaydedilir.
     * <p>
     * Rate limit: Kullanıcı başına dakikada 10 istek
     *
     * @param file     Yüklenecek multipart dosya
     * @return Yüklenen dosya meta verisini içeren FileUploadResponse
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(
            capacity = 10,
            refillTokens = 10,
            refillDurationMinutes = 1,
            keyType = KeyType.USER,
            banThreshold = 5,
            banDurationMinutes = 15
    )
    public ResponseEntity<ApiResponse<FileUploadResponse>> uploadFile(
            @RequestPart("file") MultipartFile file) {

        UUID userId = getCurrentUserId();

        log.info("User {} uploading file: {}, size: {}",
                userId, file.getOriginalFilename(), file.getSize());

        FileUploadResponse response = fileService.uploadFile(file, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Dosya başarıyla yüklendi"));
    }

    /**
     * POST /api/v1/files/upload/multiple
     * Sisteme birden fazla dosyayı aynı anda yükler.
     * <p>
     * Her dosya bağımsız olarak doğrulanır, depolanır ve işlenir.
     * Doğrulama başarısız olan dosyalar atlanır, başarılı yüklemeler meta verilerini döndürür.
     * <p>
     * Rate limit: Kullanıcı başına dakikada 10 istek
     *
     * @param files    Yklenecek multipart dosya listesi
     * @return Başarıyla yüklenen dosyaların meta verisini içeren FileUploadResponse listesi
     */
    @PostMapping(value = "/upload/multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RateLimit(
            capacity = 10,
            refillTokens = 10,
            refillDurationMinutes = 1,
            keyType = KeyType.USER,
            banThreshold = 5,
            banDurationMinutes = 15
    )
    public ResponseEntity<ApiResponse<List<FileUploadResponse>>> uploadFiles(
            @RequestPart("files") List<MultipartFile> files) {

        UUID userId = getCurrentUserId();

        if (files == null || files.isEmpty()) {
            return ResponseEntity
                    .badRequest()
                    .body(ApiResponse.fail("Dosya seçilmedi"));
        }

        log.info("User {} uploading {} files", userId, files.size());

        List<FileUploadResponse> responses = fileService.uploadFiles(files, userId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(responses, "%d dosya başarıyla yüklendi".formatted(responses.size())));
    }

    /**
     * GET /api/v1/files
     * Geçerli kullanıcının sahip olduğu dosyaların sayfalanmış listesini alır.
     * <p>
     * Kategoriye göre filtreleme, dosya adına göre arama, sıralama
     * ve sayfalama destekler. Sadece kullanıcıya ait görünür dosyaları döndürür.
     * <p>
     * Rate limit: Kullanıcı başına dakikada 30 istek
     *
     * @param queryDto Sorgu parametreleri (sayfalama, filtreleme, sıralama)
     * @return Dosya meta verilerini içeren FileResponse nesnelerinin sayfası
     */
    @GetMapping
    @RateLimit(
            capacity = 30,
            refillTokens = 30,
            refillDurationMinutes = 1,
            keyType = KeyType.USER,
            banThreshold = 10,
            banDurationMinutes = 10
    )
    public ResponseEntity<ApiResponse<Page<FileResponse>>> getMyFiles(
            @Valid FileQueryDto queryDto) {

        UUID userId = getCurrentUserId();

        log.debug("User {} fetching files with query: {}", userId, queryDto);

        Page<FileResponse> files = fileService.getMyFiles(userId, queryDto);

        return ResponseEntity.ok(ApiResponse.ok(files));
    }

    /**
     * GET /api/v1/files/{uuid}
     * Belirli bir dosya için detaylı meta veri alır.
     * <p>
     * Sadece dosya sahibi veya admin dosya meta verilerine erişebilir.
     * Dosya mevcut ve görünür olmalıdır.
     * <p>
     * Rate limit: Kullanıcı başına dakikada 60 istek
     *
     * @param fileId Alınacak dosyanın ID'si
     * @return Detaylı dosya meta verilerini içeren FileResponse
     */
    @GetMapping("/{uuid}")
    @RateLimit(
            capacity = 60,
            refillTokens = 60,
            refillDurationMinutes = 1,
            keyType = KeyType.USER,
            banThreshold = 15,
            banDurationMinutes = 5
    )
    public ResponseEntity<ApiResponse<FileResponse>> getFileById(
            @PathVariable("uuid") UUID fileId) {

        UUID userId = getCurrentUserId();

        log.debug("User {} fetching file metadata for: {}", userId, fileId);

        FileResponse response = fileService.getFileById(fileId, userId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/files/{uuid}/download
     * Depolamadan bir dosya indirir.
     * <p>
     * Sadece dosya sahibi veya admin dosyaları indirebilir.
     * Dosya diskte mevcut olmalı ve veritabanında görünür olmalıdır.
     * <p>
     * Rate limit: Kullanıcı başına dakikada 60 istek
     *
     * @param fileId İndirilecek dosyanın ID'si
     * @return İndirme için dosyayı temsil eden Resource
     */
    @GetMapping("/{uuid}/download")
    @RateLimit(
            capacity = 60,
            refillTokens = 60,
            refillDurationMinutes = 1,
            keyType = KeyType.USER,
            banThreshold = 15,
            banDurationMinutes = 5
    )
    public ResponseEntity<Resource> downloadFile(
            @PathVariable("uuid") UUID fileId) {

        UUID userId = getCurrentUserId();

        log.info("User {} downloading file: {}", userId, fileId);

        Resource resource = fileService.downloadFile(fileId, userId);

        // Set Content-Disposition header to force download
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * GET /api/v1/files/{uuid}/view/{size}
     * Bir görsel dosyası için thumbnail alır.
     * <p>
     * Sadece dosya sahibi veya admin thumbnail'ları görüntüleyebilir.
     * Thumbnail'lar üç boyutta oluşturulur:
     * <ul>
     *   <li>sm (small): 160x160</li>
     *   <li>md (medium): 480x480</li>
     *   <li>lg (large): 1280x1280</li>
     * </ul>
     * <p>
     * Rate limit: Kullanıcı başına dakikada 60 istek
     *
     * @param fileId Görsel dosyasının ID'si
     * @param size   Thumbnail boyutu (sm, md veya lg)
     * @return Thumbnail görselini temsil eden Resource
     */
    @GetMapping("/{uuid}/view/{size}")
    @RateLimit(
            capacity = 60,
            refillTokens = 60,
            refillDurationMinutes = 1,
            keyType = KeyType.USER,
            banThreshold = 15,
            banDurationMinutes = 5
    )
    public ResponseEntity<Resource> viewThumbnail(
            @PathVariable("uuid") UUID fileId,
            @PathVariable("size") String size) {

        UUID userId = getCurrentUserId();

        log.debug("User {} viewing thumbnail for file: {}, size: {}", userId, fileId, size);

        Resource resource = fileService.viewThumbnail(fileId, size, userId);

        // Set Content-Type header for inline viewing
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_JPEG_VALUE)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    /**
     * DELETE /api/v1/files/{uuid}
     * Sistemden bir dosyayı siler.
     * <p>
     * Sadece dosya sahibi veya admin dosyaları silebilir.
     * Veritabanında dosyayı görünmez olarak işaretleyerek soft delete yapar.
     * Fiziksel dosya diskte kalır ancak API üzerinden erişilemez.
     * <p>
     * Rate limit: Kullanıcı başına dakikada 20 istek
     *
     * @param fileId Silinecek dosyanın ID'si
     * @return Başarı mesajı
     */
    @DeleteMapping("/{uuid}")
    @RateLimit(
            capacity = 20,
            refillTokens = 20,
            refillDurationMinutes = 1,
            keyType = KeyType.USER,
            banThreshold = 5,
            banDurationMinutes = 10
    )
    public ResponseEntity<ApiResponse<Void>> deleteFile(
            @PathVariable("uuid") UUID fileId) {

        UUID userId = getCurrentUserId();

        log.info("User {} deleting file: {}", userId, fileId);

        fileService.deleteFile(fileId, userId);

        return ResponseEntity.ok(ApiResponse.ok("Dosya başarıyla silindi"));
    }

    /**
     * Güvenlik bağlamından geçerli kullanıcının ID'sini alır.
     *
     * @return Şu anda kimliği doğrulanmış kullanıcının UUID'si
     * @throws IllegalStateException Kullanıcı kimliği doğrulanmamışsa
     */
    private UUID getCurrentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof AppUserDetails appUserDetails) {
            return appUserDetails.getId();
        }

        throw new IllegalStateException("Kullanıcı kimliği doğrulanmamış");
    }
}
