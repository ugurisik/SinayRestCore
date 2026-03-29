package com.sinay.core.service;

import com.sinay.core.core.ObjectCore;
import com.sinay.core.dto.request.*;
import com.sinay.core.dto.response.AuthResponse;
import com.sinay.core.dto.response.UserResponse;
import com.sinay.core.entity.QUser;
import com.sinay.core.entity.Role;
import com.sinay.core.entity.User;
import com.sinay.core.exception.BadRequestException;
import com.sinay.core.exception.ConflictException;
import com.sinay.core.exception.ResourceNotFoundException;
import com.sinay.core.exception.UnauthorizedException;
import com.sinay.core.mail.MailService;
import com.sinay.core.mapper.UserMapper;
import com.sinay.core.security.jwt.JwtUtil;
import com.querydsl.core.types.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final MailService mailService;
    private final UserMapper userMapper;

    // ===== REGISTER =====

    @Transactional
    public UserResponse register(RegisterRequest req) {
        // Email ve username unique kontrolü
        QUser q = QUser.user;
        if (ObjectCore.exists(q, q.email.eq(req.getEmail().toLowerCase()).and(q.visible.isTrue()))) {
            throw new ConflictException("Bu email adresi zaten kullanımda: " + req.getEmail());
        }
        if (ObjectCore.exists(q, q.username.eq(req.getUsername().toLowerCase()).and(q.visible.isTrue()))) {
            throw new ConflictException("Bu kullanıcı adı zaten alınmış: " + req.getUsername());
        }

        // USER rolünü bul
        ObjectCore.Result<Role> roleResult = ObjectCore.getByField(Role.class, "name", Role.RoleName.USER);
        if (!roleResult.isSuccess()) {
            throw new ResourceNotFoundException("USER rolü bulunamadı. DataInitializer çalıştı mı?");
        }
        Role userRole = roleResult.getData();

        String verificationToken = UUID.randomUUID().toString();

        User user = User.builder()
                .name(req.getName())
                .surname(req.getSurname())
                .username(req.getUsername().toLowerCase())
                .email(req.getEmail().toLowerCase())
                .password(passwordEncoder.encode(req.getPassword()))
                .phone(req.getPhone())
                .enabled(false)  // mail doğrulaması bekliyor
                .accountLocked(false)
                .emailVerificationToken(verificationToken)
                .roles(Set.of(userRole))
                .build();

        ObjectCore.Result<User> saved = ObjectCore.save(user);
        mailService.sendVerificationEmail(saved.getData().getEmail(), saved.getData().getName(), verificationToken);

        log.info("New user registered: {}", saved.getData().getEmail());
        return userMapper.toResponse(saved.getData());
    }

    // ===== LOGIN =====

    @Transactional
    public AuthResponse login(LoginRequest req, HttpServletRequest httpRequest) {
        // Spring Security authentication — bad credentials, disabled, locked kontrolü burada
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(req.getIdentifier(), req.getPassword())
        );

        // Email veya username ile bul
        QUser q = QUser.user;
        Predicate emailPredicate = q.email.eq(req.getIdentifier()).and(q.visible.isTrue());
        Predicate usernamePredicate = q.username.eq(req.getIdentifier()).and(q.visible.isTrue());

        Predicate p = q.visible.isTrue().and(q.email.eq(req.getIdentifier()).or(q.username.eq(req.getIdentifier())));

        ObjectCore.Result<User> result = ObjectCore.findOne(q,p);
        User user = result.getDataOrNull();

      /*  ObjectCore.Result<User> emailResult = ObjectCore.findOne(q, emailPredicate);
        ObjectCore.Result<User> usernameResult = ObjectCore.findOne(q, usernamePredicate);

        User user = emailResult.isSuccess()
                ? emailResult.getData()
                : usernameResult.isSuccess()
                        ? usernameResult.getData()
                        : null;
*/
        if (user == null) {
            throw new UnauthorizedException("Kullanıcı bulunamadı");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String accessToken = jwtUtil.generateAccessToken(userDetails);
        String refreshToken = jwtUtil.generateRefreshToken(userDetails);

        // Refresh token'ı DB'ye kaydet
        user.setRefreshToken(refreshToken);
        user.setRefreshTokenExpiresAt(
                LocalDateTime.now().plusSeconds(jwtUtil.getRefreshExpirationMs() / 1000)
        );
        user.setLastLoginAt(LocalDateTime.now());
        user.setLastLoginIp(getClientIp(httpRequest));
        ObjectCore.save(user);

        log.info("User logged in: {}", user.getEmail());

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtUtil.getExpirationMs())
                .user(userMapper.toResponse(user))
                .build();
    }

    // ===== REFRESH TOKEN =====

    @Transactional
    public AuthResponse refreshToken(RefreshTokenRequest req) {
        String refreshToken = req.getRefreshToken();

        if (!jwtUtil.isTokenValid(refreshToken)) {
            throw new UnauthorizedException("Refresh token geçersiz veya süresi dolmuş");
        }

        QUser q = QUser.user;
        Predicate predicate = q.refreshToken.eq(refreshToken).and(q.visible.isTrue());
        ObjectCore.Result<User> result = ObjectCore.findOne(q, predicate);

        if (!result.isSuccess()) {
            throw new UnauthorizedException("Refresh token bulunamadı");
        }

        User user = result.getData();

        if (user.getRefreshTokenExpiresAt() != null &&
                user.getRefreshTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh token süresi dolmuş");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());

        String newAccessToken = jwtUtil.generateAccessToken(userDetails);
        String newRefreshToken = jwtUtil.generateRefreshToken(userDetails);

        user.setRefreshToken(newRefreshToken);
        user.setRefreshTokenExpiresAt(
                LocalDateTime.now().plusSeconds(jwtUtil.getRefreshExpirationMs() / 1000)
        );
        ObjectCore.save(user);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtUtil.getExpirationMs())
                .user(userMapper.toResponse(user))
                .build();
    }

    // ===== LOGOUT =====

    @Transactional
    public void logout(UUID userId) {
        ObjectCore.Result<User> result = ObjectCore.getById(User.class, userId);

        if (result.isSuccess()) {
            User user = result.getData();
            user.setRefreshToken(null);
            user.setRefreshTokenExpiresAt(null);
            ObjectCore.save(user);
            log.info("User logged out: {}", user.getEmail());
        }
    }

    // ===== EMAIL VERIFICATION =====

    @Transactional
    public void verifyEmail(String token) {
        QUser q = QUser.user;
        Predicate predicate = q.emailVerificationToken.eq(token).and(q.visible.isTrue());

        ObjectCore.Result<User> result = ObjectCore.findOne(q, predicate);

        if (!result.isSuccess()) {
            throw new BadRequestException("Geçersiz token!");
        }

        User user = result.getData();

        if (user.getEmailVerifiedAt() != null) {
            throw new BadRequestException("Bu email adresi zaten doğrulanmış");
        }

        user.setEnabled(true);
        user.setEmailVerifiedAt(LocalDateTime.now());
        user.setEmailVerificationToken(null);
        ObjectCore.save(user);

        mailService.sendWelcomeEmail(user.getEmail(), user.getName());
        log.info("Email verified for user: {}", user.getEmail());
    }

    // ===== FORGOT PASSWORD =====

    @Transactional
    public void forgotPassword(ForgotPasswordRequest req) {
        // Güvenlik: kullanıcı bulunamasa da aynı mesajı dön — email enumeration'ı engelle
        ObjectCore.Result<User> result = ObjectCore.getByField(User.class, "email", req.getEmail().toLowerCase());
        if (result.isSuccess()) {
            User user = result.getData();
            String resetToken = UUID.randomUUID().toString();
            user.setPasswordResetToken(resetToken);
            user.setPasswordResetTokenExpiresAt(LocalDateTime.now().plusHours(1));
            ObjectCore.save(user);
            mailService.sendPasswordResetEmail(user.getEmail(), user.getName(), resetToken);
            log.info("Password reset requested for: {}", user.getEmail());
        }
    }

    // ===== RESET PASSWORD =====

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        QUser q = QUser.user;
        Predicate predicate = q.passwordResetToken.eq(req.getToken()).and(q.visible.isTrue());

        ObjectCore.Result<User> result = ObjectCore.findOne(q, predicate);

        if (!result.isSuccess()) {
            throw new BadRequestException("Geçersiz veya kullanılmış şifre sıfırlama token'ı");
        }

        User user = result.getData();

        if (user.getPasswordResetTokenExpiresAt() == null ||
                user.getPasswordResetTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BadRequestException("Şifre sıfırlama token'ının süresi dolmuş. Lütfen tekrar talep edin");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setPasswordResetToken(null);
        user.setPasswordResetTokenExpiresAt(null);
        // Tüm aktif session'ları geçersiz kıl
        user.setRefreshToken(null);
        user.setRefreshTokenExpiresAt(null);
        ObjectCore.save(user);

        mailService.sendPasswordChangedEmail(user.getEmail(), user.getName());
        log.info("Password reset completed for: {}", user.getEmail());
    }

    // ===== HELPER =====

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader != null && !xfHeader.isEmpty()) {
            return xfHeader.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
