package com.sinay.core.exception;

import lombok.Getter;

@Getter
public class UsException extends RuntimeException {

    private final UsErrorCode errorCode;

    public UsException(String message, UsErrorCode errorCode) {
        super(message);
        this.errorCode = errorCode;
    }

    public static void firlat(String message, UsErrorCode errorCode) {
        throw new UsException(message, errorCode);
    }
}