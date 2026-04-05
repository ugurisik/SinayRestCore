package com.sinay.core.server.service;

import com.sinay.core.server.core.ObjectCore;
import com.sinay.core.server.dto.response.PageResponse;
import com.sinay.core.server.dto.response.UserResponse;
import com.sinay.core.server.entity.QRole;
import com.sinay.core.server.entity.QUser;
import com.sinay.core.server.entity.Role;
import com.sinay.core.server.entity.User;
import com.sinay.core.server.exception.UsErrorCode;
import com.sinay.core.server.exception.UsException;
import com.sinay.core.server.mapper.UserMapper;
import com.querydsl.core.types.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserMapper userMapper;

    /**
     * Tüm kullanıcıları listeler — filtreli, sayfalı.
     */
    @Transactional
    public PageResponse<UserResponse> getAllUsers(String search, Role.RoleName role, Boolean enabled, Pageable pageable) {
        // QueryDSL ile sorgu oluştur
        QUser q = QUser.user;
        Predicate predicate = q.visible.isTrue();

        if (search != null && !search.isBlank()) {
            String searchTerm = search.toLowerCase();
            predicate = q.name.containsIgnoreCase(searchTerm)
                    .or(q.surname.containsIgnoreCase(searchTerm))
                    .or(q.email.containsIgnoreCase(searchTerm))
                    .or(q.username.containsIgnoreCase(searchTerm))
                    .and(predicate);
        }

        if (role != null) {
            predicate = q.roles.any().name.eq(role).and(predicate);
        }

        if (enabled != null) {
            predicate = q.enabled.eq(enabled).and(predicate);
        }

        ObjectCore.ListResult<User> result = ObjectCore.list(q, predicate, pageable);

        return PageResponse.of(
            new org.springframework.data.domain.PageImpl<>(
                result.getData().stream().map(userMapper::toResponse).toList(),
                pageable,
                result.getTotal()
            )
        );
    }

    /**
     * ID ile kullanıcı getirir.
     */
    @Transactional
    public UserResponse getUserById(UUID id) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, id);

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + id, UsErrorCode.RESOURCE_NOT_FOUND);
        }
        return userMapper.toResponse(result.getData());
    }

    /**
     * Kullanıcıya rol ekler.
     */
    @Transactional
    public void addRoleToUser(UUID userId, String roleNameStr) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userId);

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userId, UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();

        Role.RoleName roleName;
        try {
            roleName = Role.RoleName.valueOf(roleNameStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            UsException.firlat("Geçersiz rol: " + roleNameStr, UsErrorCode.BAD_REQUEST);
            return; // Never reached, but satisfies compiler
        }

        if (user.hasRole(roleName)) {
            UsException.firlat("Kullanıcı zaten bu role sahip: " + roleName, UsErrorCode.BAD_REQUEST);
        }

        // Rolü ObjectCore ile bul
        QRole qRole = QRole.role;
        ObjectCore.Result<Role> roleResult = ObjectCore.getByField(Role.class, "name", roleName);

        if (!roleResult.isSuccess()) {
            UsException.firlat("Role bulunamadı: " + roleName, UsErrorCode.RESOURCE_NOT_FOUND);
        }

        Role role = roleResult.getData();

        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.add(role);
        user.setRoles(roles);

        ObjectCore.save(user);
        log.info("Role {} added to user {}", roleName, userId);
    }

    /**
     * Kullanıcıdan rol kaldırır.
     */
    @Transactional
    public void removeRoleFromUser(UUID userId, String roleNameStr) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userId);

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userId, UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();

        Role.RoleName roleName;
        try {
            roleName = Role.RoleName.valueOf(roleNameStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            UsException.firlat("Geçersiz rol: " + roleNameStr, UsErrorCode.BAD_REQUEST);
            return; // Never reached, but satisfies compiler
        }

        if (!user.hasRole(roleName)) {
            UsException.firlat("Kullanıcının bu rolü yok: " + roleName, UsErrorCode.BAD_REQUEST);
        }

        // MASTER_ADMIN rolü kendisinden çıkarılamaz
        if (user.isMasterAdmin() && roleName == Role.RoleName.MASTER_ADMIN) {
            UsException.firlat("MASTER_ADMIN rolü kaldırılamaz", UsErrorCode.BAD_REQUEST);
        }

        Set<Role> roles = new HashSet<>(user.getRoles());
        roles.removeIf(r -> r.getName() == roleName);
        user.setRoles(roles);

        ObjectCore.save(user);
        log.info("Role {} removed from user {}", roleName, userId);
    }

    /**
     * Kullanıcı hesabını kilitler.
     */
    @Transactional
    public void lockUser(UUID userId) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userId);

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userId, UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();

        if (user.getAccountLocked()) {
            UsException.firlat("Kullanıcı zaten kilitli", UsErrorCode.BAD_REQUEST);
        }

        user.setAccountLocked(true);
        user.setRefreshToken(null);
        user.setRefreshTokenExpiresAt(null);
        ObjectCore.save(user);

        log.info("User locked: {}", userId);
    }

    /**
     * Kilitli kullanıcı hesabını açar.
     */
    @Transactional
    public void unlockUser(UUID userId) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userId);

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userId, UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();

        if (!user.getAccountLocked()) {
            UsException.firlat("Kullanıcı kilitli değil", UsErrorCode.BAD_REQUEST);
        }

        user.setAccountLocked(false);
        ObjectCore.save(user);

        log.info("User unlocked: {}", userId);
    }

    /**
     * Kullanıcı hesabını aktif eder (enabled = true).
     */
    @Transactional
    public void enableUser(UUID userId) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userId);

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userId, UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();

        if (user.getEnabled()) {
            UsException.firlat("Kullanıcı zaten aktif", UsErrorCode.BAD_REQUEST);
        }

        user.setEnabled(true);
        ObjectCore.save(user);

        log.info("User enabled: {}", userId);
    }

    /**
     * Kullanıcıyı soft delete ile siler.
     */
    @Transactional
    public void deleteUser(UUID userId) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userId);

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userId, UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();

        // MASTER_ADMIN silinemez
        if (user.isMasterAdmin()) {
            UsException.firlat("MASTER_ADMIN silinemez", UsErrorCode.BAD_REQUEST);
        }

        ObjectCore.Result<Void> deleteResult = ObjectCore.delete(user);
        if (!deleteResult.isSuccess()) {
            UsException.firlat(deleteResult.getError(), UsErrorCode.BAD_REQUEST);
        }

        log.info("User deleted (soft): {}", userId);
    }
}
