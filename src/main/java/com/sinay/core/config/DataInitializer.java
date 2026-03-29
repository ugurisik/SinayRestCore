package com.sinay.core.config;

import com.sinay.core.core.ObjectCore;
import com.sinay.core.entity.QRole;
import com.sinay.core.entity.QUser;
import com.sinay.core.entity.Role;
import com.sinay.core.entity.User;
import com.querydsl.core.types.Predicate;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.HashSet;
import java.util.Set;

/**
 * Uygulama ilk başladığında çalışır ve varsayılan verileri oluşturur:
 * - MASTER_ADMIN, ADMIN, USER rolleri
 * - application.yaml'deki admin bilgileriyle MASTER_ADMIN kullanıcısı
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final PasswordEncoder passwordEncoder;
    private final AdminProperties adminProperties;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initializeData() {
        log.info("🚀 DataInitializer başlatılıyor...");

        createRolesIfNotExists();
        createMasterAdminIfNotExists();

        log.info("✅ DataInitializer tamamlandı.");
    }

    private void createRolesIfNotExists() {
        for (Role.RoleName roleName : Role.RoleName.values()) {
            QRole q = QRole.role;
            Predicate predicate = q.name.eq(roleName).and(q.visible.isTrue());

            if (!ObjectCore.exists(q, predicate)) {
                Role role = Role.builder()
                        .name(roleName)
                        .description(getRoleDescription(roleName))
                        .build();
                ObjectCore.save(role);
                log.info("📌 Rol oluşturuldu: {}", roleName);
            }
        }
    }

    private void createMasterAdminIfNotExists() {
        QUser q = QUser.user;
        Predicate predicate = q.email.eq(adminProperties.getEmail()).and(q.visible.isTrue());

        if (ObjectCore.exists(q, predicate)) {
            log.info("👤 MASTER_ADMIN zaten mevcut: {}", adminProperties.getEmail());
            return;
        }

        ObjectCore.Result<Role> roleResult = ObjectCore.getByField(Role.class, "name", Role.RoleName.MASTER_ADMIN);
        if (!roleResult.isSuccess()) {
            throw new IllegalStateException("MASTER_ADMIN rolü bulunamadı!");
        }
        Role masterAdminRole = roleResult.getData();

        User masterAdmin = User.builder()
                .name(adminProperties.getName())
                .surname(adminProperties.getSurname())
                .username("admin")
                .email(adminProperties.getEmail())
                .password(passwordEncoder.encode(adminProperties.getPassword()))
                .enabled(true)
                .accountLocked(false)
                .emailVerifiedAt(java.time.LocalDateTime.now())
                .roles(Set.of(masterAdminRole))
                .build();

        ObjectCore.save(masterAdmin);
        log.info("👑 MASTER_ADMIN oluşturuldu: {} ({})", adminProperties.getEmail(), adminProperties.getPassword());
    }

    private String getRoleDescription(Role.RoleName roleName) {
        return switch (roleName) {
            case MASTER_ADMIN -> "Sistem yöneticisi - tüm yetkiler";
            case ADMIN -> "Yönetici - yönetimsel yetkiler";
            case USER -> "Standart kullanıcı";
        };
    }

    @Configuration
    @ConfigurationProperties(prefix = "app.admin")
    @RequiredArgsConstructor
    public static class AdminProperties {
        private String email;
        private String password;
        private String name;
        private String surname;

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getSurname() { return surname; }
        public void setSurname(String surname) { this.surname = surname; }
    }
}
