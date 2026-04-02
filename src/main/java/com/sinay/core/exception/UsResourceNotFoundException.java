package com.sinay.core.exception;

import java.util.UUID;

public class UsResourceNotFoundException extends RuntimeException {
    public UsResourceNotFoundException(String resource, UUID id) {
        super(resource + " bulunamadı: " + id);
    }

    public UsResourceNotFoundException(String resource, String identifier) {
        super(resource + " bulunamadı: " + identifier);
    }

    public UsResourceNotFoundException(String message) {
        super(message);
    }
}