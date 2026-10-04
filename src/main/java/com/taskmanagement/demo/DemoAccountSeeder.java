package com.taskmanagement.demo;

import com.taskmanagement.entity.User;
import com.taskmanagement.entity.UserRole;
import com.taskmanagement.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Activates the V2 placeholder accounts only in a local/demo application. */
@Component
@Profile("demo & !prod")
@EnableConfigurationProperties(DemoAccountProperties.class)
public class DemoAccountSeeder implements ApplicationRunner {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final DemoAccountProperties properties;

    public DemoAccountSeeder(UserRepository users, PasswordEncoder passwords,
                             DemoAccountProperties properties) {
        this.users = users;
        this.passwords = passwords;
        this.properties = properties;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        validatePassword(properties.userPassword(), "DEMO_USER_PASSWORD");
        validatePassword(properties.adminPassword(), "DEMO_ADMIN_PASSWORD");
        activate("phuc@example.com", "demo_password_hash_2", properties.userPassword(), UserRole.USER);
        activate("admin@example.com", "demo_password_hash_3", properties.adminPassword(), UserRole.ADMIN);
    }

    private static void validatePassword(String value, String variable) {
        if (value == null || value.isBlank() || value.length() < 8
                || value.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException(variable + " must contain 8 to 72 UTF-8 bytes");
        }
    }

    private void activate(String email, String placeholderHash, String rawPassword, UserRole role) {
        User user = users.findByEmail(email).orElse(null);
        if (user == null) {
            user = new User();
            user.setEmail(email);
            user.setRole(role);
            user.setPasswordHash(passwords.encode(rawPassword));
            users.save(user);
            return;
        }
        if (user.getRole() != role) {
            throw new IllegalStateException("Demo account has an unexpected role: " + email);
        }
        if (placeholderHash.equals(user.getPasswordHash())) {
            user.setPasswordHash(passwords.encode(rawPassword));
        } else if (!passwords.matches(rawPassword, user.getPasswordHash())) {
            throw new IllegalStateException("Demo account already has different credentials: " + email);
        }
    }
}
