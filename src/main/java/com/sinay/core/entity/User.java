package com.sinay.core.entity;

import com.sinay.core.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_user_email", columnList = "email", unique = true),
        @Index(name = "idx_user_username", columnList = "username", unique = true)
})
public class User extends BaseEntity {

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "email", nullable = false, unique = true, length = 100)
    private String email;

    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "surname", nullable = false, length = 50)
    private String surname;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled = false;  // mail doğrulaması sonrası true

    @Column(name = "account_locked", nullable = false)
    private Boolean accountLocked = false;

    // ===== EMAIL VERIFICATION =====
    @Column(name = "email_verification_token", length = 255)
    private String emailVerificationToken;

    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    // ===== PASSWORD RESET =====
    @Column(name = "password_reset_token", length = 255)
    private String passwordResetToken;

    @Column(name = "password_reset_token_expires_at")
    private LocalDateTime passwordResetTokenExpiresAt;

    // ===== REFRESH TOKEN =====
    @Column(name = "refresh_token", length = 512)
    private String refreshToken;

    @Column(name = "refresh_token_expires_at")
    private LocalDateTime refreshTokenExpiresAt;

    // ===== LAST LOGIN =====
    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "last_login_ip", length = 45)
    private String lastLoginIp;

    // ===== ROLES =====
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    @Builder.Default
    private Set<Role> roles = new HashSet<>();

    // ===== HELPER METHODS =====

    public String getFullName() {
        return name + " " + surname;
    }

    public boolean hasRole(Role.RoleName roleName) {
        return roles.stream().anyMatch(r -> r.getName() == roleName);
    }

    public boolean isMasterAdmin() {
        return hasRole(Role.RoleName.MASTER_ADMIN);
    }

    @Override
    public Boolean getLog() {
        return true;
    }
}