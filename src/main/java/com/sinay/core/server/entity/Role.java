package com.sinay.core.server.entity;

import com.sinay.core.server.base.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "roles")
public class Role extends BaseEntity {

    /**
     * Core'da sadece MASTER_ADMIN var.
     * Projeye göre buraya ekleme yapılır: ADMIN, USER, MODERATOR vs.
     */
    public enum RoleName {
        MASTER_ADMIN,
        ADMIN,
        USER
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "name", nullable = false, unique = true, length = 50)
    private RoleName name;

    @Column(name = "description", length = 255)
    private String description;

    @ManyToMany(mappedBy = "roles", fetch = FetchType.LAZY)
    private Set<User> users = new HashSet<>();
}