package com.sinay.core.server.controller;

import com.sinay.core.server.dto.response.ApiResponse;
import com.sinay.core.server.dto.response.PageResponse;
import com.sinay.core.server.dto.response.UserResponse;
import com.sinay.core.server.entity.Role;
import com.sinay.core.server.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MASTER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    /**
     * GET /api/v1/admin/users
     * Tüm kullanıcıları listeler — filtreli, sayfalı.
     * Query params: search (text), role (MASTER_ADMIN, ADMIN, USER), enabled (true/false)
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<UserResponse>>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role.RoleName role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<UserResponse> response = adminService.getAllUsers(search, role, enabled, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * GET /api/v1/admin/users/{id}
     * ID ile kullanıcı getirir.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse> getUserById(@PathVariable String id) {
        UserResponse response = adminService.getUserById(java.util.UUID.fromString(id));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * POST /api/v1/admin/users/{id}/roles/{role}
     * Kullanıcıya rol ekler.
     */
    @PostMapping("/{id}/roles/{role}")
    public ResponseEntity<ApiResponse> addRoleToUser(@PathVariable String id, @PathVariable String role) {
        adminService.addRoleToUser(UUID.fromString(id), role);
        return ResponseEntity.ok(ApiResponse.ok("Rol ataması yapıldı"));
    }

    /**
     * DELETE /api/v1/admin/users/{id}/roles/{role}
     * Kullanıcıdan rol kaldırır.
     */
    @DeleteMapping("/{id}/roles/{role}")
    public ResponseEntity<ApiResponse> removeRoleFromUser(@PathVariable String id, @PathVariable String role) {
        adminService.removeRoleFromUser(UUID.fromString(id), role);
        return ResponseEntity.ok(ApiResponse.ok("Rol ataması kaldırıldı!"));
    }

    /**
     * POST /api/v1/admin/users/{id}/lock
     * Kullanıcı hesabını kilitler.
     */
    @PostMapping("/{id}/lock")
    public ResponseEntity<ApiResponse> lockUser(@PathVariable String id) {
        adminService.lockUser(UUID.fromString(id));
        return ResponseEntity.ok(ApiResponse.ok("Kullanıcı hesabı kilitlendi."));
    }

    /**
     * POST /api/v1/admin/users/{id}/unlock
     * Kilitli kullanıcı hesabını açar.
     */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<ApiResponse> unlockUser(@PathVariable String id) {
        adminService.unlockUser(UUID.fromString(id));
        return ResponseEntity.ok(ApiResponse.ok("Hesabın kilidi kaldırıldı."));
    }

    /**
     * POST /api/v1/admin/users/{id}/enable
     * Kullanıcı hesabını aktif eder.
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<ApiResponse> enableUser(@PathVariable String id) {
        adminService.enableUser(UUID.fromString(id));
        return ResponseEntity.ok(ApiResponse.ok("Hesap tekrardan aktif hale getirildi"));
    }

    /**
     * DELETE /api/v1/admin/users/{id}
     * Kullanıcıyı soft delete ile siler.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse> deleteUser(@PathVariable String id) {
        adminService.deleteUser( UUID.fromString(id));
        return ResponseEntity.ok(ApiResponse.ok("Hesap silindi..."));
    }
}
