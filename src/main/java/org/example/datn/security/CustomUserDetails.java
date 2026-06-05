package org.example.datn.security;

import lombok.Getter;
import org.example.datn.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
public class CustomUserDetails implements UserDetails {

    private final Long userId;
    private final String phone;
    private final String password;
    private final String role;
    private final boolean active;

    public CustomUserDetails(User user) {
        this.userId = user.getUserId();
        this.phone = user.getPhone();
        this.password = user.getPassword();
        this.role = user.getRole().name();
        this.active = Boolean.TRUE.equals(user.getStatus());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }

    @Override
    public String getPassword() {
        return password;
    }

    /** Username for Spring Security is the phone number. */
    @Override
    public String getUsername() {
        return phone;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
