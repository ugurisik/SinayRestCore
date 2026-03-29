package com.sinay.core.controller;

import com.sinay.core.dto.response.PageResponse;
import com.sinay.core.dto.response.UserResponse;
import com.sinay.core.entity.Role;
import com.sinay.core.service.AdminService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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
    public ResponseEntity<PageResponse<UserResponse>> getAllUsers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Role.RoleName role,
            @RequestParam(required = false) Boolean enabled,
            @PageableDefault(size = 20) Pageable pageable) {
        PageResponse<UserResponse> response = adminService.getAllUsers(search, role, enabled, pageable);
        return ResponseEntity.ok(response);
    }

    /**
     * GET /api/v1/admin/users/{id}
     * ID ile kullanıcı getirir.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUserById(@PathVariable String id) {
        UserResponse response = adminService.getUserById(java.util.UUID.fromString(id));
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/admin/users/{id}/roles/{role}
     * Kullanıcıya rol ekler.
     */
    @PostMapping("/{id}/roles/{role}")
    public ResponseEntity<Void> addRoleToUser(@PathVariable String id, @PathVariable String role) {
        adminService.addRoleToUser(java.util.UUID.fromString(id), role);
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/admin/users/{id}/roles/{role}
     * Kullanıcıdan rol kaldırır.
     */
    @DeleteMapping("/{id}/roles/{role}")
    public ResponseEntity<Void> removeRoleFromUser(@PathVariable String id, @PathVariable String role) {
        adminService.removeRoleFromUser(java.util.UUID.fromString(id), role);
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/users/{id}/lock
     * Kullanıcı hesabını kilitler.
     */
    @PostMapping("/{id}/lock")
    public ResponseEntity<Void> lockUser(@PathVariable String id) {
        adminService.lockUser(java.util.UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/users/{id}/unlock
     * Kilitli kullanıcı hesabını açar.
     */
    @PostMapping("/{id}/unlock")
    public ResponseEntity<Void> unlockUser(@PathVariable String id) {
        adminService.unlockUser(java.util.UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * POST /api/v1/admin/users/{id}/enable
     * Kullanıcı hesabını aktif eder.
     */
    @PostMapping("/{id}/enable")
    public ResponseEntity<Void> enableUser(@PathVariable String id) {
        adminService.enableUser(java.util.UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }

    /**
     * DELETE /api/v1/admin/users/{id}
     * Kullanıcıyı soft delete ile siler.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable String id) {
        adminService.deleteUser(java.util.UUID.fromString(id));
        return ResponseEntity.noContent().build();
    }
}
