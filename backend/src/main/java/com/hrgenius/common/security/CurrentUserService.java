package com.hrgenius.common.security;

import com.hrgenius.auth.entity.User;
import com.hrgenius.auth.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Resolves the acting user from the security context. The JWT carries only the email and
 * authorities, so the linked employee id is looked up on demand.
 */
@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Email (JWT subject) of the authenticated caller, or "system" outside a request. */
    public String email() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth == null ? "system" : auth.getName();
    }

    public Optional<User> user() {
        return userRepository.findByEmailIgnoreCase(email());
    }

    /** Employee id linked to the caller's login, if any (admins may have none). */
    public Optional<Long> employeeId() {
        return user().map(User::getEmployeeId);
    }

    public boolean hasAuthority(String authority) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return false;
        }
        for (GrantedAuthority ga : auth.getAuthorities()) {
            if (ga.getAuthority().equals(authority)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasRole(String role) {
        return hasAuthority("ROLE_" + role);
    }
}
