package com.sinay.core.server.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sinay.core.server.entity.Role;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserResponse {

    private final UUID id;
    private final String username;
    private final String email;
    private final String name;
    private final String surname;
    private final String fullName;
    private final String phone;
    private final Boolean enabled;
    private final Boolean accountLocked;
    private final Set<Role.RoleName> roles;
    private final LocalDateTime lastLoginAt;
    private final LocalDateTime createdAt;
}