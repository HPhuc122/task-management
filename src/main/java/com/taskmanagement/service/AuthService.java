package com.taskmanagement.service;

import com.taskmanagement.dto.*;
import com.taskmanagement.entity.User;
import com.taskmanagement.entity.UserRole;
import com.taskmanagement.exception.DuplicateEmailException;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.security.CurrentUser;
import com.taskmanagement.security.DemoAccountAccessPolicy;
import com.taskmanagement.security.JwtService;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final JwtService tokens;
    private final CurrentUser currentUser;
    private final DemoAccountAccessPolicy demoAccounts;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, JwtService tokens,
                       CurrentUser currentUser, DemoAccountAccessPolicy demoAccounts) {
        this.users = users;
        this.passwords = passwords;
        this.tokens = tokens;
        this.currentUser = currentUser;
        this.demoAccounts = demoAccounts;
        this.dummyHash = passwords.encode("dummy-password-for-timing-only");
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (users.existsByEmail(request.email())) {
            throw new DuplicateEmailException();
        }
        User user = new User();
        user.setEmail(request.email());
        user.setPasswordHash(passwords.encode(request.password()));
        user.setRole(UserRole.USER);
        return tokens.issue(users.saveAndFlush(user));
    }

    public AuthResponse login(LoginRequest request) {
        if (demoAccounts.blocks(request.email())) {
            throw new BadCredentialsException("Invalid email or password");
        }
        User user = users.findByEmail(request.email()).orElse(null);
        boolean matches = passwords.matches(request.password(), user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !matches) {
            throw new BadCredentialsException("Invalid email or password");
        }
        return tokens.issue(user);
    }

    public UserResponse me() {
        return users.findById(currentUser.id()).map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("User no longer exists"));
    }
}
