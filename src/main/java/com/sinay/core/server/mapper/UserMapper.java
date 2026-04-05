package com.sinay.core.server.mapper;

import com.sinay.core.server.dto.response.UserResponse;
import com.sinay.core.server.entity.Role;
import com.sinay.core.server.entity.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * MapStruct mapper — compile time'da implement edilir, reflection yok, performanslı.
 * ObjectCore'daki copyPojoToRecord() mantığının type-safe versiyonu.
 */
@Mapper(componentModel = "spring")
public interface UserMapper {

    @Mapping(target = "fullName", expression = "java(user.getFullName())")
    @Mapping(target = "roles", source = "roles", qualifiedByName = "rolesToRoleNames")
    UserResponse toResponse(User user);

    @Named("rolesToRoleNames")
    default Set<Role.RoleName> rolesToRoleNames(Set<Role> roles) {
        if (roles == null) return Set.of();
        return roles.stream()
                .map(Role::getName)
                .collect(Collectors.toSet());
    }
}