package com.sinay.core.core;

/**
 * JSON serileştirme/deserileştirme hatası.
 * <p>
 * ObjectCore serialize/deserialize metodlarında fırlatılır.
 */
public class SerializationException extends RuntimeException {

    private final ErrorCode errorCode;

    public SerializationException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SerializationException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
