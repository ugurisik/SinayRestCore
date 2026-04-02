package com.sinay.core.exception;

import com.sinay.core.dto.response.ApiResponse;
import com.sinay.core.fileupload.exception.FileStorageException;
import com.sinay.core.fileupload.exception.FileUploadException;
import com.sinay.core.fileupload.exception.FileValidationException;
import com.sinay.core.fileupload.exception.UploadedFileNotFoundException;
import com.sinay.core.ratelimit.exception.RateLimitBanException;
import com.sinay.core.ratelimit.exception.RateLimitException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ===== VALIDATION =====

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String field = ((FieldError) error).getField();
            String message = error.getDefaultMessage();
            errors.put(field, message);
        });
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail("Validasyon hatası", UsErrorCode.VALIDATION_ERROR, errors));
    }

    // ===== CUSTOM EXCEPTION =====

    @ExceptionHandler(UsException.class)
    public ResponseEntity<ApiResponse<Void>> handleUs(UsException ex) {
        if (ex.getErrorCode().getHttpStatus().is5xxServerError()) {
            log.error("UsException [{}]: {}", ex.getErrorCode(), ex.getMessage(), ex);
        }
        return ResponseEntity
                .status(ex.getErrorCode().getHttpStatus())
                .body(ApiResponse.fail(ex.getMessage(), ex.getErrorCode()));
    }

    // ===== SECURITY =====

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail("Bu kaynağa erişim yetkiniz yok", UsErrorCode.ACCESS_DENIED));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("Email/kullanıcı adı veya şifre hatalı", UsErrorCode.BAD_CREDENTIALS));
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabled(DisabledException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("Hesabınız henüz aktif değil. Lütfen email adresinizi doğrulayın", UsErrorCode.ACCOUNT_DISABLED));
    }

    @ExceptionHandler(LockedException.class)
    public ResponseEntity<ApiResponse<Void>> handleLocked(LockedException ex) {
        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.fail("Hesabınız kilitlendi. Lütfen destek ekibiyle iletişime geçin", UsErrorCode.ACCOUNT_LOCKED));
    }

    // ===== RATE LIMITING =====

    @ExceptionHandler(RateLimitException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimit(RateLimitException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.fail(ex.getMessage(), UsErrorCode.RATE_LIMIT_EXCEEDED));
    }

    @ExceptionHandler(RateLimitBanException.class)
    public ResponseEntity<ApiResponse<Void>> handleRateLimitBan(RateLimitBanException ex) {
        return ResponseEntity
                .status(HttpStatus.TOO_MANY_REQUESTS)
                .body(ApiResponse.fail(ex.getMessage(), UsErrorCode.RATE_LIMIT_BANNED));
    }

    // ===== FILE UPLOAD =====

    @ExceptionHandler(UploadedFileNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadedFileNotFound(UploadedFileNotFoundException ex) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ex.getMessage(), UsErrorCode.FILE_NOT_FOUND));
    }

    @ExceptionHandler(FileValidationException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileValidation(FileValidationException ex) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ex.getMessage(), UsErrorCode.FILE_VALIDATION_ERROR));
    }

    @ExceptionHandler(FileStorageException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileStorage(FileStorageException ex) {
        log.error("File storage error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ex.getMessage(), UsErrorCode.FILE_STORAGE_ERROR));
    }

    @ExceptionHandler(FileUploadException.class)
    public ResponseEntity<ApiResponse<Void>> handleFileUpload(FileUploadException ex) {
        log.error("File upload error: {}", ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ex.getMessage(), UsErrorCode.FILE_UPLOAD_ERROR));
    }

    // ===== FALLBACK =====

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneral(Exception ex, WebRequest request) {
        log.error("Unhandled exception: {} - {}", ex.getClass().getSimpleName(), ex.getMessage(), ex);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("Beklenmeyen bir hata oluştu. Lütfen tekrar deneyiniz", UsErrorCode.INTERNAL_ERROR));
    }
}