package com.sinay.core.server.fileupload.exception;

/**
 * Dosya doğrulaması başarısız olduğunda fırlatılan exception.
 * Şunları içeren doğrulama hataları:
 * - Geçersiz dosya türü
 * - Dosya boyutu limitleri aşıyor
 * - Geçersiz dosya adı
 * - Gerekli meta veri eksik
 */
public class FileValidationException extends FileUploadException {

    /**
     * Varsayılan kurucu.
     */
    public FileValidationException() {
        super();
    }

    /**
     * Hata mesajlı kurucu.
     *
     * @param message Doğrulama hatasını tanımlayan hata mesajı.
     */
    public FileValidationException(String message) {
        super(message);
    }

    /**
     * Hata mesajı ve alt nedeneli kurucu.
     *
     * @param message Doğrulama hatasını tanımlayan hata mesajı.
     * @param cause   Doğrulama hatasının altında yatan neden.
     */
    public FileValidationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Dosya türü doğrulama hataları için kurucu.
     *
     * @param fileType     Sağlanan geçersiz dosya türü.
     * @param allowedTypes İzin verilen dosya türleri.
     * @return Açıklayıcı mesajlı FileValidationException.
     */
    public static FileValidationException invalidFileType(String fileType, String allowedTypes) {
        return new FileValidationException(
            String.format("Geçersiz dosya türü: '%s'. İzin verilen türler: %s", fileType, allowedTypes)
        );
    }

    /**
     * Dosya boyutu doğrulama hataları için kurucu.
     *
     * @param fileSize Gerçek dosya boyutu.
     * @param maxSize  İzin verilen maksimum dosya boyutu.
     * @return Açıklayıcı mesajlı FileValidationException.
     */
    public static FileValidationException fileTooLarge(long fileSize, long maxSize) {
        return new FileValidationException(
            String.format("Dosya boyutu %d bayt, izin verilen maksimum boyut %d baytı aşıyor", fileSize, maxSize)
        );
    }

    /**
     * Boş dosya doğrulama hataları için kurucu.
     *
     * @return Açıklayıcı mesajlı FileValidationException.
     */
    public static FileValidationException emptyFile() {
        return new FileValidationException("Boş dosya yüklenemez");
    }
}
