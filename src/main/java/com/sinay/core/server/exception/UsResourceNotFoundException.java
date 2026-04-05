package com.sinay.core.server.exception;

import java.util.UUID;

/**
 * Kaynak bulunamadı exception'ı.
 */
public class UsResourceNotFoundException extends RuntimeException {

    public UsResourceNotFoundException(String resource, UUID id) {
        UsException.firlat(resource + " bulunamadı: " + id, UsErrorCode.RESOURCE_NOT_FOUND);
    }

    public UsResourceNotFoundException(String resource, String identifier) {
        UsException.firlat(resource + " bulunamadı: " + identifier, UsErrorCode.RESOURCE_NOT_FOUND);
    }

    public UsResourceNotFoundException(String message) {
        UsException.firlat(message, UsErrorCode.RESOURCE_NOT_FOUND);
    }
}
