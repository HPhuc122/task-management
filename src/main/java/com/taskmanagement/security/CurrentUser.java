package com.taskmanagement.security;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUser {
    private Authentication authentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return auth;
    }

    public Long id() {
        return Long.valueOf(authentication().getName());
    }

    public boolean isAdmin() {
        return authentication().getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }

    public void requireOwner(Long ownerId) {
        if (!isAdmin() && !id().equals(ownerId)) {
            throw new AccessDeniedException("You do not have permission to access this resource");
        }
    }
}
