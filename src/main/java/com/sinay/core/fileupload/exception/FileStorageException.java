package com.sinay.core.fileupload.exception;

/**
 * Dosya depolama işlemleri başarısız olduğunda fırlatılan exception.
 * Şunları içeren depolama hataları:
 * - Diske dosya yazılamıyor
 * - Yetersiz disk alanı
 * - İzin reddedildi
 * - Veritabanı depolama hataları
 * - Ağ depolama hataları
 */
public class FileStorageException extends FileUploadException {

    /**
     * Varsayılan kurucu.
     */
    public FileStorageException() {
        super();
    }

    /**
     * Hata mesajlı kurucu.
     *
     * @param message Depolama hatasını tanımlayan hata mesajı.
     */
    public FileStorageException(String message) {
        super(message);
    }

    /**
     * Hata mesajı ve alt nedeneli kurucu.
     *
     * @param message Depolama hatasını tanımlayan hata mesajı.
     * @param cause   Depolama hatasının altında yatan neden.
     */
    public FileStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Dosya yazma hataları için kurucu.
     *
     * @param filePath Dosyanın yazılamadığı yol.
     * @param cause    Yazma hatasının altında yatan neden.
     * @return Açıklayıcı mesajlı FileStorageException.
     */
    public static FileStorageException unableToWriteFile(String filePath, Throwable cause) {
        return new FileStorageException(
            String.format("Dosya yazılamıyor: %s", filePath),
            cause
        );
    }

    /**
     * Yetersiz disk alanı için kurucu.
     *
     * @param requiredSpace Gerekli disk alanı (bayt cinsinden).
     * @param availableSpace Mevcut disk alanı (bayt cinsinden).
     * @return Açıklayıcı mesajlı FileStorageException.
     */
    public static FileStorageException insufficientDiskSpace(long requiredSpace, long availableSpace) {
        return new FileStorageException(
            String.format("Yetersiz disk alanı. Gerekli: %d bayt, Mevcut: %d bayt",
                         requiredSpace, availableSpace)
        );
    }

    /**
     * Dosya silme hataları için kurucu.
     *
     * @param filePath Silinmeyen dosyanın yolu.
     * @param cause    Silme hatasının altında yatan neden.
     * @return Açıklayıcı mesajlı FileStorageException.
     */
    public static FileStorageException unableToDeleteFile(String filePath, Throwable cause) {
        return new FileStorageException(
            String.format("Dosya silinemiyor: %s", filePath),
            cause
        );
    }
}
