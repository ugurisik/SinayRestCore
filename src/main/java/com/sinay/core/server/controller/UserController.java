package com.sinay.core.server.controller;

import com.sinay.core.server.dto.request.ChangePasswordRequest;
import com.sinay.core.server.dto.request.UpdateUserRequest;
import com.sinay.core.server.dto.response.UserResponse;
import com.sinay.core.server.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * GET /api/v1/users/me
     * Giriş yapmış kullanıcının profil bilgilerini getirir.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getMyProfile() {
        UserResponse response = userService.getMyProfile();
        return ResponseEntity.ok(response);
    }

    /**
     * PATCH /api/v1/users/me
     * Giriş yapmış kullanıcının profil bilgilerini günceller.
     */
    @PatchMapping("/me")
    public ResponseEntity<UserResponse> updateMyProfile(@Valid @RequestBody UpdateUserRequest request) {
        UserResponse response = userService.updateMyProfile(request);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/users/me/change-password
     * Giriş yapmış kullanıcının şifresini değiştirir.
     */
    @PostMapping("/me/change-password")
    public ResponseEntity<Void> changeMyPassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changeMyPassword(request);
        return ResponseEntity.noContent().build();
    }
}
