package com.hrgenius.common.security;

import com.hrgenius.auth.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Adapts our {@link User} to Spring Security. Authorities include both role authorities
 * (ROLE_XYZ) and fine-grained permission codes so @PreAuthorize can check either.
 */
public class CustomUserDetails implements UserDetails {

    private final User user;
    private final List<GrantedAuthority> authorities;

    public CustomUserDetails(User user) {
        this.user = user;
        this.authorities = buildAuthorities(user);
    }

    private static List<GrantedAuthority> buildAuthorities(User user) {
        List<GrantedAuthority> list = new ArrayList<>();
        user.getRoles().forEach(role -> {
            list.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
            role.getPermissions().forEach(p -> list.add(new SimpleGrantedAuthority(p.getCode())));
        });
        return list;
    }

    public Long getUserId() {
        return user.getId();
    }

    public Long getEmployeeId() {
        return user.getEmployeeId();
    }

    public String getFullName() {
        return user.getFullName();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return user.getEmail();
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !user.isLocked();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return user.getStatus() == User.UserStatus.ACTIVE;
    }
}
