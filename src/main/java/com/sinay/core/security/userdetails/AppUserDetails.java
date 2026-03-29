package com.sinay.core.security.userdetails;

import com.sinay.core.entity.User;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Spring Security {@link UserDetails} implementasyonu.
 * <p>
 * Kullanıcı UUID'sini ve diğer bilgileri güvenlik bağlamında tutmak için kullanılır.
 * JWT filtresinde Authentication oluştururken bu sınıf kullanılır.
 */
@Getter
public class AppUserDetails implements UserDetails {

    private final UUID id;
    private final String email;
    private final String password;
    private final String username;
    private final Set<GrantedAuthority> authorities;
    private final boolean accountLocked;
    private final boolean enabled;

    /**
     * User entity'sinden AppUserDetails oluşturur.
     *
     * @param user Kullanıcı entity'si
     * @return AppUserDetails nesnesi
     */
    public static AppUserDetails from(User user) {
        Set<GrantedAuthority> authorities = user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.getName().name()))
                .collect(Collectors.toSet());

        return new AppUserDetails(
                user.getId(),
                user.getEmail(),
                user.getPassword(),
                user.getUsername() != null ? user.getUsername() : user.getEmail(),
                authorities,
                Boolean.TRUE.equals(user.getAccountLocked()),
                Boolean.TRUE.equals(user.getEnabled())
        );
    }

    private AppUserDetails(UUID id,
                           String email,
                           String password,
                           String username,
                           Set<GrantedAuthority> authorities,
                           boolean accountLocked,
                           boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.username = username;
        this.authorities = authorities;
        this.accountLocked = accountLocked;
        this.enabled = enabled;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
