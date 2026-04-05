package com.sinay.core.server.exception;

/**
 * Yetkisiz erişim exception'ı.
 */
public class UsUnauthorizedException extends RuntimeException {

    public UsUnauthorizedException(String message) {
        UsException.firlat(message, UsErrorCode.UNAUTHORIZED);
    }

    public UsUnauthorizedException() {
        UsException.firlat("Bu işlem için yetkiniz yok", UsErrorCode.UNAUTHORIZED);
    }
}
