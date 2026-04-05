package com.sinay.core.server.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum UsErrorCode {

    // =========================
    // 400 - BAD REQUEST
    // =========================
    BAD_REQUEST(HttpStatus.BAD_REQUEST),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    INVALID_REQUEST_BODY(HttpStatus.BAD_REQUEST),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST),
    MISSING_PARAMETER(HttpStatus.BAD_REQUEST),
    TYPE_MISMATCH(HttpStatus.BAD_REQUEST),

    FILE_VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(HttpStatus.BAD_REQUEST),
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST),

    BUSINESS_RULE_VIOLATION(HttpStatus.BAD_REQUEST),

    // =========================
    // 401 - UNAUTHORIZED
    // =========================
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED),
    BAD_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED),
    ACCOUNT_DISABLED(HttpStatus.UNAUTHORIZED),
    ACCOUNT_LOCKED(HttpStatus.UNAUTHORIZED),

    // =========================
    // 403 - FORBIDDEN
    // =========================
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    INSUFFICIENT_PERMISSION(HttpStatus.FORBIDDEN),
    OPERATION_NOT_ALLOWED(HttpStatus.FORBIDDEN),

    // =========================
    // 404 - NOT FOUND
    // =========================
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    USER_NOT_FOUND(HttpStatus.NOT_FOUND),
    FILE_NOT_FOUND(HttpStatus.NOT_FOUND),

    // =========================
    // 405 - METHOD NOT ALLOWED
    // =========================
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED),

    // =========================
    // 409 - CONFLICT
    // =========================
    CONFLICT(HttpStatus.CONFLICT),
    DUPLICATE_RESOURCE(HttpStatus.CONFLICT),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT),
    USERNAME_ALREADY_EXISTS(HttpStatus.CONFLICT),

    // =========================
    // 410 - GONE
    // =========================
    RESOURCE_DELETED(HttpStatus.GONE),

    // =========================
    // 415 - UNSUPPORTED MEDIA TYPE
    // =========================
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),

    // =========================
    // 422 - UNPROCESSABLE ENTITY
    // =========================
    UNPROCESSABLE_ENTITY(HttpStatus.UNPROCESSABLE_ENTITY),
    INVALID_STATE(HttpStatus.UNPROCESSABLE_ENTITY),

    // =========================
    // 429 - TOO MANY REQUESTS
    // =========================
    RATE_LIMIT_EXCEEDED(HttpStatus.TOO_MANY_REQUESTS),
    RATE_LIMIT_BANNED(HttpStatus.TOO_MANY_REQUESTS),

    // =========================
    // 500 - INTERNAL SERVER ERROR
    // =========================
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    UNEXPECTED_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    FILE_STORAGE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_UPLOAD_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),
    FILE_DELETE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    DATABASE_ERROR(HttpStatus.INTERNAL_SERVER_ERROR),

    // =========================
    // 503 - SERVICE UNAVAILABLE
    // =========================
    SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    EXTERNAL_SERVICE_ERROR(HttpStatus.SERVICE_UNAVAILABLE);

    private final HttpStatus httpStatus;

    UsErrorCode(HttpStatus httpStatus) {
        this.httpStatus = httpStatus;
    }
}