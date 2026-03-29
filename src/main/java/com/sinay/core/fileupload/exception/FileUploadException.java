package com.sinay.core.fileupload.exception;

/**
 * Tüm dosya yükleme hataları için temel exception sınıfı.
 * Bu exception, daha spesifik dosya yükleme exception'ları için üst sınıf olarak hizmet eder.
 */
public class FileUploadException extends RuntimeException {

    /**
     * Varsayılan kurucu.
     */
    public FileUploadException() {
        super();
    }

    /**
     * Hata mesajlı kurucu.
     *
     * @param message Exception'ı tanımlayan hata mesajı.
     */
    public FileUploadException(String message) {
        super(message);
    }

    /**
     * Hata mesajı ve alt nedeneli kurucu.
     *
     * @param message Exception'ı tanımlayan hata mesajı.
     * @param cause   Exception'ın altında yatan neden.
     */
    public FileUploadException(String message, Throwable cause) {
        super(message, cause);
    }
}
