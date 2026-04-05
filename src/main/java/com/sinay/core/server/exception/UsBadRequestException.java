package com.sinay.core.server.exception;

/**
 * Kötü istek exception'ı.
 */
public class UsBadRequestException extends RuntimeException {

    public UsBadRequestException(String message) {
        UsException.firlat(message, UsErrorCode.BAD_REQUEST);
    }
}
