package com.sinay.core.exception;

public class UsUnauthorizedException extends RuntimeException {
    public UsUnauthorizedException(String message) {
        super(message);
    }
    public UsUnauthorizedException() {
        super("Bu işlem için yetkiniz yok");
    }
}