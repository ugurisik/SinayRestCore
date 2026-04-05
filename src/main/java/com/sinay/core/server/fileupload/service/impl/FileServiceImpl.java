package com.sinay.core.server.fileupload.service.impl;

import com.sinay.core.server.core.ObjectCore;
import com.sinay.core.server.exception.UsErrorCode;
import com.sinay.core.server.exception.UsException;
import com.sinay.core.server.fileupload.dto.FileQueryDto;
import com.sinay.core.server.fileupload.dto.FileResponse;
import com.sinay.core.server.fileupload.dto.FileUploadResponse;
import com.sinay.core.server.fileupload.entities.UploadedFile;
import com.sinay.core.server.fileupload.entities.QUploadedFile;
import com.sinay.core.server.fileupload.enums.FileCategory;
import com.sinay.core.server.fileupload.exception.UploadedFileNotFoundException;
import com.sinay.core.server.fileupload.service.FileService;
import com.sinay.core.server.fileupload.service.FileStorageService;
import com.sinay.core.server.fileupload.service.FileValidationService;
import com.sinay.core.server.fileupload.service.ImageProcessingService;
import com.sinay.core.server.utils.StringUtils;
import com.querydsl.core.types.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * FileService arayüzünün implementasyonu.
 * <p>
 * Bu servis, doğrulama, depolama, görsel işleme ve veritabanı kalıcılığı arasında
 * koordinasyon sağlayarak tam dosya yükleme iş akışını yönetir.
 * <p>
 * Temel özellikler:
 * <ul>
 *   <li>Depolamadan önce kapsamlı dosya doğrulama</li>
 *   <li>Görsel dosyalar için otomatik thumbnail oluşturma</li>
 *   <li>Sahiplik tabanlı erişim kontrolü</li>
 *   <li>Güvenli dosya kaldırma için soft delete</li>
 *   <li>Filtreleme ve sıralama ile sayfalanmış dosya listesi</li>
 *   <li>Graceful failure handling ile çoklu dosya yükleme</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final FileStorageService fileStorageService;
    private final FileValidationService fileValidationService;
    private final ImageProcessingService imageProcessingService;

    @Override
    @Transactional
    public FileUploadResponse uploadFile(MultipartFile file, UUID userId) {
        // Kategoriyi otomatik belirle (MIME type veya extension'dan)
        FileCategory category = determineCategory(file);
        log.info("Starting file upload for user {} in category {}", userId, category);

        // Adım 1: Dosyayı doğrula
        log.debug("Validating file: {}", file.getOriginalFilename());
        fileValidationService.validateFile(file, category);

        // Adım 2: Benzersiz dosya ID'si ve depolama yolları oluştur
        UUID fileId = UUID.randomUUID();
        String storagePath = fileStorageService.getStoragePath(category, fileId);

        // Adım 3: Dosya adını sanitize et ve depolama dosya adı oluştur
        String originalFilename = file.getOriginalFilename();
        String sanitizedFilename = fileValidationService.sanitizeFilename(originalFilename);
        String extension = StringUtils.getFileExtension(originalFilename);
        String storedFilename = fileId + "_original." + extension;

        // Adım 4: Dosyayı diske depola
        String fullPath = storagePath + storedFilename;
        log.debug("Storing file at: {}", fullPath);
        String absolutePath = fileStorageService.storeFile(file, fullPath);

        // Adım 5: Görsel dosyalar için thumbnail oluştur
        Map<String, String> thumbnailPaths = new HashMap<>();
        if (category.shouldGenerateThumbnails()) {
            log.debug("Generating thumbnails for image file");
            try {
                File storedFile = new File(absolutePath);
                File storageDir = storedFile.getParentFile();
                thumbnailPaths = imageProcessingService.generateThumbnails(storedFile, storageDir);
                log.info("Generated {} thumbnails", thumbnailPaths.size());
            } catch (IOException e) {
                log.error("Failed to generate thumbnails for file {}", fileId, e);
                // Yükleme başarısız olmak yerine thumbnailsuz devam et
            }
        }

        // Adım 6: Görsel dosyalar için görsel boyutlarını çıkar
        Integer imageWidth = null;
        Integer imageHeight = null;
        if (category == FileCategory.IMAGE) {
            try {
                File imageFile = new File(absolutePath);
                int[] dimensions = imageProcessingService.getImageDimensions(imageFile);
                imageWidth = dimensions[0];
                imageHeight = dimensions[1];
                log.debug("Image dimensions: {}x{}", imageWidth, imageHeight);
            } catch (IOException e) {
                log.warn("Failed to extract image dimensions for file {}", fileId, e);
            }
        }

        // Adım 7: Veritabanı entity'si oluştur
        UploadedFile uploadedFile = UploadedFile.builder()
                .ownerUserId(userId)
                .category(category)
                .originalFilename(originalFilename)
                .storedFilename(storedFilename)
                .extension(extension)
                .mimeType(file.getContentType())
                .fileSize(file.getSize())
                .storagePath(fullPath)
                .imageWidth(imageWidth)
                .imageHeight(imageHeight)
                .thumbnailSmPath(thumbnailPaths.get(ImageProcessingService.SIZE_SMALL))
                .thumbnailMdPath(thumbnailPaths.get(ImageProcessingService.SIZE_MEDIUM))
                .thumbnailLgPath(thumbnailPaths.get(ImageProcessingService.SIZE_LARGE))
                .build();

        // Adım 8: Veritabanına kaydet
        ObjectCore.Result<UploadedFile> saveResult = ObjectCore.save(uploadedFile);
        UploadedFile savedFile = saveResult.getData();
        log.info("File uploaded successfully with ID: {}", savedFile.getId());

        // Adım 9: Yanıt oluştur
        return FileUploadResponse.builder()
                .id(savedFile.getId())
                .originalFilename(savedFile.getOriginalFilename())
                .storedFilename(savedFile.getStoredFilename())
                .category(savedFile.getCategory())
                .extension(savedFile.getExtension())
                .fileSize(savedFile.getFileSize())
                .storagePath(savedFile.getStoragePath())
                .thumbnailSmPath(savedFile.getThumbnailSmPath())
                .thumbnailMdPath(savedFile.getThumbnailMdPath())
                .thumbnailLgPath(savedFile.getThumbnailLgPath())
                .createdAt(savedFile.getCreatedAt())
                .build();
    }

    @Override
    @Transactional
    public List<FileUploadResponse> uploadFiles(List<MultipartFile> files, UUID userId) {
        log.info("Starting multiple file upload for user {}, total files: {}",
                userId, files.size());

        List<FileUploadResponse> successfulUploads = new ArrayList<>();
        int failedCount = 0;

        for (int i = 0; i < files.size(); i++) {
            MultipartFile file = files.get(i);

            // Boş dosyaları atla
            if (file == null || file.isEmpty()) {
                log.warn("Skipping empty file at index {}", i);
                failedCount++;
                continue;
            }

            try {
                log.debug("Processing file {}/{}: {}", i + 1, files.size(), file.getOriginalFilename());

                // Her dosyayı ayrı ayrı işle (kategori otomatik belirlenecek)
                FileUploadResponse response = uploadFile(file, userId);
                successfulUploads.add(response);

                log.debug("Successfully processed file {} at index {}", file.getOriginalFilename(), i);

            } catch (Exception e) {
                // Hatayı logla ama diğer dosyaları işlemeye devam et
                failedCount++;
                log.error("Failed to upload file '{}' at index {}: {}",
                        file.getOriginalFilename(), i, e.getMessage(), e);

                // Kalan dosyaları işlemeye devam et
            }
        }

        log.info("Multiple file upload completed. Successful: {}, Failed: {}",
                successfulUploads.size(), failedCount);

        return successfulUploads;
    }

    @Override
    public Page<FileResponse> getMyFiles(UUID userId, FileQueryDto queryDto) {
        log.debug("Fetching files for user {} with query: {}", userId, queryDto);

        // Query DTO'dan pageable oluştur
        Sort sort = Sort.by(
                "DESC".equalsIgnoreCase(queryDto.getSortDirection())
                        ? Sort.Direction.DESC
                        : Sort.Direction.ASC,
                queryDto.getSortBy()
        );

        Pageable pageable = PageRequest.of(queryDto.getPage(), queryDto.getSize(), sort);

        // Veritabanından dosyaları al
        QUploadedFile q = QUploadedFile.uploadedFile;
        Predicate predicate = q.ownerUserId.eq(userId).and(q.visible.isTrue());
        ObjectCore.ListResult<UploadedFile> result = ObjectCore.list(q, predicate, null);
        List<UploadedFile> userFiles = result.getData();

        // Belirtilmişse filtreleri uygula
        List<UploadedFile> filteredFiles = new ArrayList<>(userFiles);

        if (queryDto.getCategory() != null) {
            filteredFiles.removeIf(file -> file.getCategory() != queryDto.getCategory());
        }

        if (queryDto.getSearch() != null && !queryDto.getSearch().isBlank()) {
            String searchTerm = queryDto.getSearch().toLowerCase();
            filteredFiles.removeIf(file ->
                    !file.getOriginalFilename().toLowerCase().contains(searchTerm)
            );
        }

        // Manuel sıralama uygula (basitleştirilmiş yaklaşım)
        Sort.Direction direction = "DESC".equalsIgnoreCase(queryDto.getSortDirection())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;

        Comparator<UploadedFile> comparator = getComparator(queryDto.getSortBy(), direction);
        filteredFiles.sort(comparator);

        // Sayfalama uygula
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), filteredFiles.size());

        List<UploadedFile> paginatedFiles = filteredFiles.subList(
                Math.min(start, filteredFiles.size()),
                Math.min(end, filteredFiles.size())
        );

        // DTO'lara dönüştür
        List<FileResponse> fileResponses = paginatedFiles.stream()
                .map(this::mapToFileResponse)
                .toList();

        // Manuel sayfa oluştur (basitleştirilmiş)
        return new org.springframework.data.domain.PageImpl<>(
                fileResponses,
                pageable,
                filteredFiles.size()
        );
    }

    @Override
    public FileResponse getFileById(UUID fileId, UUID userId) {
        log.debug("Fetching file {} for user {}", fileId, userId);

        // Veritabanından dosyayı al
        ObjectCore.Result<UploadedFile> result = ObjectCore.getById(UploadedFile.class, fileId);

        if (!result.isSuccess()) {
            log.warn("File not found: {}", fileId);
            throw UploadedFileNotFoundException.byUuid(fileId.toString());
        }

        UploadedFile uploadedFile = result.getData();

        // Sahipliği kontrol et (sahibi veya admin erişebilir)
        checkOwnership(uploadedFile, userId, "erişmek");

        return mapToFileResponse(uploadedFile);
    }

    @Override
    public Resource downloadFile(UUID fileId, UUID userId) {
        log.debug("Downloading file {} for user {}", fileId, userId);

        // Veritabanından dosyayı al
        ObjectCore.Result<UploadedFile> result = ObjectCore.getById(UploadedFile.class, fileId);

        if (!result.isSuccess()) {
            log.warn("File not found: {}", fileId);
            throw UploadedFileNotFoundException.byUuid(fileId.toString());
        }

        UploadedFile uploadedFile = result.getData();

        // Sahipliği kontrol et (sahibi veya admin indirebilir)
        checkOwnership(uploadedFile, userId, "indirmek");

        // Diskten dosyayı oku
        File file = fileStorageService.readFile(uploadedFile.getStoragePath());

        log.info("File {} downloaded by user {}", fileId, userId);
        return new FileSystemResource(file);
    }

    @Override
    @Transactional
    public void deleteFile(UUID fileId, UUID userId) {
        log.debug("Deleting file {} for user {}", fileId, userId);

        // Veritabanından dosyayı al
        ObjectCore.Result<UploadedFile> result = ObjectCore.getById(UploadedFile.class, fileId);

        if (!result.isSuccess()) {
            log.warn("File not found: {}", fileId);
            throw UploadedFileNotFoundException.byUuid(fileId.toString());
        }

        UploadedFile uploadedFile = result.getData();

        // Sahipliği kontrol et (sahibi veya admin silebilir)
        checkOwnership(uploadedFile, userId, "silmek");

        // Soft delete
        ObjectCore.Result<Void> deleteResult = ObjectCore.delete(uploadedFile);
        if (!deleteResult.isSuccess()) {
            UsException.firlat("Dosya silinemedi: " + deleteResult.getError(), UsErrorCode.FILE_DELETE_ERROR);
        }

        log.info("File {} soft-deleted by user {}", fileId, userId);
    }

    @Override
    public Resource viewThumbnail(UUID fileId, String size, UUID userId) {
        log.debug("Viewing thumbnail for file {}, size: {}, user: {}", fileId, size, userId);

        // Boyut parametresini doğrula
        if (!isValidThumbnailSize(size)) {
            UsException.firlat(
                    "Geçersiz thumbnail boyutu: " + size + ". Şunlardan biri olmalı: sm, md, lg",
                    UsErrorCode.INVALID_PARAMETER
            );
        }

        // Veritabanından dosyayı al
        ObjectCore.Result<UploadedFile> result = ObjectCore.getById(UploadedFile.class, fileId);

        if (!result.isSuccess()) {
            log.warn("File not found: {}", fileId);
            throw UploadedFileNotFoundException.byUuid(fileId.toString());
        }

        UploadedFile uploadedFile = result.getData();

        // Sahipliği kontrol et (sahibi veya admin görüntüleyebilir)
        checkOwnership(uploadedFile, userId, "thumbnail'ını görüntülemek");

        // Boyuta göre thumbnail yolunu al
        String thumbnailPath = switch (size.toLowerCase()) {
            case ImageProcessingService.SIZE_SMALL -> uploadedFile.getThumbnailSmPath();
            case ImageProcessingService.SIZE_MEDIUM -> uploadedFile.getThumbnailMdPath();
            case ImageProcessingService.SIZE_LARGE -> uploadedFile.getThumbnailLgPath();
            default -> null;
        };

        if (thumbnailPath == null || thumbnailPath.isBlank()) {
            log.warn("Thumbnail not available for file {} with size {}", fileId, size);
            throw new UploadedFileNotFoundException(
                    "Thumbnail mevcut değil, boyut: " + size
            );
        }

        // Diskten thumbnail oku
        File thumbnailFile = fileStorageService.readFile(thumbnailPath);

        log.debug("Thumbnail {} served for file {}", size, fileId);
        return new FileSystemResource(thumbnailFile);
    }

    /**
     * Kullanıcının dosyanın sahibi olup olmadığını kontrol eder.
     * <p>
     * Gerçek bir uygulamada bu, kullanıcının admin yetkisine sahip olup olmadığını da kontrol eder.
     * Şu anda sadece sahipliği kontrol eder.
     *
     * @param uploadedFile Sahipliği kontrol edilecek dosya
     * @param userId      Dosyaya erişmeye çalışan kullanıcının ID'si
     * @param action      Gerçekleştirilen işlem (hata mesajları için)
     * @throws org.springframework.security.access.AccessDeniedException Kullanıcı sahibi değilse
     */
    private void checkOwnership(UploadedFile uploadedFile, UUID userId, String action) {
        if (!uploadedFile.getOwnerUserId().equals(userId)) {
            log.warn("User {} attempted to {} file {} owned by {}",
                    userId, action, uploadedFile.getId(), uploadedFile.getOwnerUserId());
            throw new org.springframework.security.access.AccessDeniedException(
                    "Bu dosyayı " + action + " için yetkiniz yok"
            );
        }
    }

    /**
     * Bir UploadedFile entity'sini FileResponse DTO'suna eşler.
     *
     * @param uploadedFile Eşlenecek entity
     * @return FileResponse DTO
     */
    private FileResponse mapToFileResponse(UploadedFile uploadedFile) {
        return FileResponse.builder()
                .id(uploadedFile.getId())
                .ownerUserId(uploadedFile.getOwnerUserId())
                .ownerUsername(null) // Would be populated by joining with users table if needed
                .category(uploadedFile.getCategory())
                .originalFilename(uploadedFile.getOriginalFilename())
                .storedFilename(uploadedFile.getStoredFilename())
                .extension(uploadedFile.getExtension())
                .mimeType(uploadedFile.getMimeType())
                .fileSize(uploadedFile.getFileSize())
                .checksum(uploadedFile.getChecksum())
                .storagePath(uploadedFile.getStoragePath())
                .imageWidth(uploadedFile.getImageWidth())
                .imageHeight(uploadedFile.getImageHeight())
                .thumbnailSmPath(uploadedFile.getThumbnailSmPath())
                .thumbnailMdPath(uploadedFile.getThumbnailMdPath())
                .thumbnailLgPath(uploadedFile.getThumbnailLgPath())
                .createdAt(uploadedFile.getCreatedAt())
                .updatedAt(uploadedFile.getUpdatedAt())
                .build();
    }

    /**
     * Dosyadan kategoriyi otomatik belirler.
     * <p>
     * Önce MIME type'a bakar, bulamazsa dosya uzantısından kategoriyi belirler.
     *
     * @param file Kategorisi belirlenecek dosya
     * @return Dosyanın kategorisi
     * @throws IllegalArgumentException Dosya türü desteklenmiyorsa
     */
    private FileCategory determineCategory(MultipartFile file) {
        String mimeType = file.getContentType();

        // Önce MIME type'tan belirlemeyi dene
        if (mimeType != null && !mimeType.isBlank()) {
            try {
                return FileCategory.fromMimeType(mimeType);
            } catch (IllegalArgumentException e) {
                log.debug("Could not determine category from MIME type '{}', trying extension", mimeType);
            }
        }

        // MIME type başarısız oldu, extension'dan dene
        String filename = file.getOriginalFilename();
        if (filename != null) {
            String extension = StringUtils.getFileExtension(filename);
            if (!extension.isBlank()) {
                try {
                    return FileCategory.fromExtension(extension);
                } catch (IllegalArgumentException e) {
                    log.debug("Could not determine category from extension '{}'", extension);
                }
            }
        }

        UsException.firlat(
                "Desteklenmeyen dosya türü. MIME type: " + mimeType +
                ", Filename: " + filename,
                UsErrorCode.UNSUPPORTED_MEDIA_TYPE
        );
        return null; // Never reached
    }

    /**
     * Thumbnail boyutunun izin verilen değerlerden biri olup olmadığını doğrular.
     *
     * @param size Doğrulanacak boyut
     * @return Boyut geçerliyse true (sm, md veya lg)
     */
    private boolean isValidThumbnailSize(String size) {
        if (size == null || size.isBlank()) {
            return false;
        }

        String sizeLower = size.toLowerCase();
        return sizeLower.equals(ImageProcessingService.SIZE_SMALL) ||
                sizeLower.equals(ImageProcessingService.SIZE_MEDIUM) ||
                sizeLower.equals(ImageProcessingService.SIZE_LARGE);
    }

    /**
     * UploadedFile entity'lerini sıralamak için bir comparator alır.
     *
     * @param sortBy    Sıralanacak alan
     * @param direction Sıralama yönü
     * @return Sıralama için comparator
     */
    private Comparator<UploadedFile> getComparator(String sortBy, Sort.Direction direction) {
        Comparator<UploadedFile> comparator = switch (sortBy) {
            case "originalFilename" -> Comparator.comparing(UploadedFile::getOriginalFilename);
            case "fileSize" -> Comparator.comparing(UploadedFile::getFileSize);
            case "category" -> Comparator.comparing(UploadedFile::getCategory);
            default -> Comparator.comparing(UploadedFile::getCreatedAt);
        };

        return direction == Sort.Direction.DESC ? comparator.reversed() : comparator;
    }
}
