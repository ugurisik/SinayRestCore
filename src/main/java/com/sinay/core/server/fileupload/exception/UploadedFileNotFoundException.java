package com.sinay.core.server.fileupload.exception;

/**
 * İstenen dosya bulunamadığında fırlatılan exception.
 * Şunları içeren senaryolar:
 * - Dosya depolamada mevcut değil
 * - Dosya ID'si geçersiz
 * - Dosya silinmiş
 * - Dosya yolu yanlış
 */
public class UploadedFileNotFoundException extends FileUploadException {

    /**
     * Varsayılan kurucu.
     */
    public UploadedFileNotFoundException() {
        super();
    }

    /**
     * Hata mesajlı kurucu.
     *
     * @param message Dosya bulunamama senaryosunu tanımlayan hata mesajı.
     */
    public UploadedFileNotFoundException(String message) {
        super(message);
    }

    /**
     * Hata mesajı ve alt nedeneli kurucu.
     *
     * @param message Dosya bulunamama senaryosunu tanımlayan hata mesajı.
     * @param cause   Exception'ın altında yatan neden.
     */
    public UploadedFileNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * ID'ye göre dosya bulunamaması için kurucu.
     *
     * @param fileId Bulunamayan dosyanın ID'si.
     * @return Açıklayıcı mesajlı UploadedFileNotFoundException.
     */
    public static UploadedFileNotFoundException byId(Long fileId) {
        return new UploadedFileNotFoundException(
            String.format("Dosya bulunamadı, ID: %d", fileId)
        );
    }

    /**
     * UUID'ye göre dosya bulunamaması için kurucu.
     *
     * @param fileUuid Bulunamayan dosyanın UUID'si.
     * @return Açıklayıcı mesajlı UploadedFileNotFoundException.
     */
    public static UploadedFileNotFoundException byUuid(String fileUuid) {
        return new UploadedFileNotFoundException(
            String.format("Dosya bulunamadı, UUID: %s", fileUuid)
        );
    }

    /**
     * Dosya adına göre dosya bulunamaması için kurucu.
     *
     * @param fileName Bulunamayan dosyanın adı.
     * @return Açıklayıcı mesajlı UploadedFileNotFoundException.
     */
    public static UploadedFileNotFoundException byFileName(String fileName) {
        return new UploadedFileNotFoundException(
            String.format("Dosya bulunamadı: %s", fileName)
        );
    }

    /**
     * Yola göre dosya bulunamaması için kurucu.
     *
     * @param filePath Dosyanın bulunamadığı yol.
     * @return Açıklayıcı mesajlı UploadedFileNotFoundException.
     */
    public static UploadedFileNotFoundException byPath(String filePath) {
        return new UploadedFileNotFoundException(
            String.format("Dosya bulunamadı, yol: %s", filePath)
        );
    }
}
