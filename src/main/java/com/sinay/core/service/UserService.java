package com.sinay.core.service;

import com.sinay.core.dto.request.ChangePasswordRequest;
import com.sinay.core.dto.request.UpdateUserRequest;
import com.sinay.core.dto.response.UserResponse;
import com.sinay.core.entity.User;
import com.sinay.core.exception.BadRequestException;
import com.sinay.core.exception.ResourceNotFoundException;
import com.sinay.core.core.ObjectCore;
import com.sinay.core.mapper.UserMapper;
import com.sinay.core.security.userdetails.AppUserDetails;
import jakarta.transaction.Transactional;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
            throw new ResourceNotFoundException("User", userDetails.getId());
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
            throw new ResourceNotFoundException("User", userDetails.getId());
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
            throw new ResourceNotFoundException("User", userDetails.getId());
        }

        User user = result.getData();
        if (!passwordEncoder.matches(req.getCurrentPassword(), user.getPassword())) {
            throw new BadRequestException("Mevcut şifre hatalı");
        }

        if (passwordEncoder.matches(req.getNewPassword(), user.getPassword())) {
            throw new BadRequestException("Yeni şifre, mevcut şifre ile aynı olamaz");
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
            throw new ResourceNotFoundException("User", id);
        }
        return userMapper.toResponse(result.getData());
    }

    /**
     * Güvenlik bağlamından geçerli kullanıcı bilgilerini alır.
     *
     * @return AppUserDetails
     * @throws IllegalStateException Kullanıcı kimliği doğrulanmamışsa
     */
    private AppUserDetails getCurrentUserDetails() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof AppUserDetails appUserDetails) {
            return appUserDetails;
        }

        throw new IllegalStateException("Kullanıcı kimliği doğrulanmamış");
    }
}
