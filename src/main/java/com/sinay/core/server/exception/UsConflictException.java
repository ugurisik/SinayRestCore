package com.sinay.core.server.exception;

/**
 * Çakışma exception'ı.
 */
public class UsConflictException extends RuntimeException {

    public UsConflictException(String message) {
        UsException.firlat(message, UsErrorCode.CONFLICT);
    }
}
