package com.sinay.core.controller;

import com.sinay.core.audit.annotation.AuditLog;
import com.sinay.core.audit.enums.AuditAction;
import com.sinay.core.dto.request.*;
import com.sinay.core.dto.response.ApiResponse;
import com.sinay.core.dto.response.AuthResponse;
import com.sinay.core.dto.response.UserResponse;
import com.sinay.core.ratelimit.annotation.RateLimit;
import com.sinay.core.ratelimit.model.KeyType;
import com.sinay.core.security.userdetails.AppUserDetails;
import com.sinay.core.service.AuthService;
import com.sinay.core.timetest.annotation.TimeTest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * POST /api/v1/auth/register
     * Yeni kullanıcı kaydı.
     */
    @PostMapping("/register")
    @RateLimit(
        capacity = 3,
        refillTokens = 3,
        refillDurationMinutes = 1,
        keyType = KeyType.IP,
        banThreshold = 3,
        banDurationMinutes = 30
    )
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        UserResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * POST /api/v1/auth/login
     * Kullanıcı girişi → access + refresh token döner.
     */
    @PostMapping("/login")
    @RateLimit(
        capacity = 5,
        refillTokens = 5,
        refillDurationMinutes = 1,
        keyType = KeyType.IP,
        banThreshold = 5,
        banDurationMinutes = 15
    )
    @TimeTest(ms = 1000, level = TimeTest.LogLevel.INFO)
   // @AuditLog(action = AuditAction.LOGIN, entityType = "USER", message = "Giriş yapıldı")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/refresh
     * Refresh token ile yeni access token alır.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/auth/logout
     * Çıkış yapar — refresh token'ı geçersiz kılar.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout() {
        AppUserDetails userDetails = (AppUserDetails) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        authService.logout(userDetails.getId());
        return ResponseEntity.ok(ApiResponse.ok("Başarılı bir şekilde çıkış yapıldı!"));
    }

    /**
     * GET /api/v1/auth/verify-email?token=xxx
     * Email doğrulama.
     */
    @GetMapping("/verify-email")
    public ResponseEntity<ApiResponse> verifyEmail(@RequestParam String token) {
        authService.verifyEmail(token);
        return ResponseEntity.ok(ApiResponse.ok("Hesabınız doğrulandı!"));
    }

    /**
     * POST /api/v1/auth/forgot-password
     * Şifre sıfırlama maili gönderir.
     */
    @PostMapping("/forgot-password")
    @RateLimit(
        capacity = 2,
        refillTokens = 2,
        refillDurationMinutes = 5,
        keyType = KeyType.IP,
        banThreshold = 3,
        banDurationMinutes = 60
    )
    public ResponseEntity<ApiResponse> forgotPassword(@Valid @RequestBody ForgotPasswordRequest request) {
        authService.forgotPassword(request);
        // Güvenlik: kullanıcı bulunamasa da aynı mesajı döneriz
        return ResponseEntity.ok(ApiResponse.ok("Şifre sıfırlama linki mail adresinize gönderildi!"));
    }

    /**
     * POST /api/v1/auth/reset-password
     * Yeni şifre belirler.
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ResponseEntity.noContent().build();
    }
}
