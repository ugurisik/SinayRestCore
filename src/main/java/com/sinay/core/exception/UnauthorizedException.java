package com.sinay.core.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) {
        super(message);
    }
    public UnauthorizedException() {
        super("Bu işlem için yetkiniz yok");
    }
}