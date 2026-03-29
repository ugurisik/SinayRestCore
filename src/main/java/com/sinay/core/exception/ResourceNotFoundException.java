package com.sinay.core.exception;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String resource, UUID id) {
        super(resource + " bulunamadı: " + id);
    }

    public ResourceNotFoundException(String resource, String identifier) {
        super(resource + " bulunamadı: " + identifier);
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }
}