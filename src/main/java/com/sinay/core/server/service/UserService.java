package com.sinay.core.server.service;

import com.sinay.core.server.dto.request.ChangePasswordRequest;
import com.sinay.core.server.dto.request.UpdateUserRequest;
import com.sinay.core.server.dto.response.UserResponse;
import com.sinay.core.server.entity.User;
import com.sinay.core.server.exception.UsErrorCode;
import com.sinay.core.server.exception.UsException;
import com.sinay.core.server.core.ObjectCore;
import com.sinay.core.server.mapper.UserMapper;
import com.sinay.core.server.security.userdetails.AppUserDetails;
import jakarta.transaction.Transactional;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {

    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Giriş yapmış kullanıcının profil bilgilerini getirir.
     */
    @Transactional
    public UserResponse getMyProfile() {
        AppUserDetails userDetails = getCurrentUserDetails();
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userDetails.getId());

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userDetails.getId(), UsErrorCode.RESOURCE_NOT_FOUND);
        }
        return userMapper.toResponse(result.getData());
    }

    /**
     * Giriş yapmış kullanıcının profil bilgilerini günceller.
     */
    @Transactional
    public UserResponse updateMyProfile(UpdateUserRequest req) {
        AppUserDetails userDetails = getCurrentUserDetails();
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userDetails.getId());

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userDetails.getId(), UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();
        if (req.getName() != null) user.setName(req.getName());
        if (req.getSurname() != null) user.setSurname(req.getSurname());
        if (req.getPhone() != null) user.setPhone(req.getPhone());

        ObjectCore.Result<User> saved = ObjectCore.save(user);
        log.info("Profile updated: {}", userDetails.getEmail());
        return userMapper.toResponse(saved.getData());
    }

    /**
     * Giriş yapmış kullanıcının şifresini değiştirir.
     * Eski şifre doğru olmalı, yeni şifre ile aynı olmamalı.
     */
    @Transactional
    public void changeMyPassword(ChangePasswordRequest req) {
        AppUserDetails userDetails = getCurrentUserDetails();
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userDetails.getId());

        if (!result.isSuccess()) {
            UsException.firlat("User bulunamadı: " + userDetails.getId(), UsErrorCode.RESOURCE_NOT_FOUND);
        }

        User user = result.getData();
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            UsException.firlat("Mevcut şifre hatalı", UsErrorCode.BAD_REQUEST);
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            UsException.firlat("Yeni şifre, mevcut şifre ile aynı olamaz", UsErrorCode.BAD_REQUEST);
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        // Tüm aktif session'ları geçersiz kıl
        user.setRefreshToken(null);
        user.setRefreshTokenExpiresAt(null);

        ObjectCore.save(user);
        log.info("Password changed for user: {}", userDetails.getEmail());
    }

    /**
     * ID ile kullanıcı getirir (admin için).
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
     * Güvenlik bağlamından geçerli kullanıcı bilgilerini alır.
     *
     * @return AppUserDetails
     */
    private AppUserDetails getCurrentUserDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof AppUserDetails appUserDetails) {
            return appUserDetails;
        }

        UsException.firlat("Kullanıcı kimliği doğrulanmamış", UsErrorCode.UNAUTHORIZED);
        return null; // Never reached, UsException.firlat always throws
    }
}
