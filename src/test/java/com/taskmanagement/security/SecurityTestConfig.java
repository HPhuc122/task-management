package com.taskmanagement.security;

import com.taskmanagement.config.CorsConfig;
import com.taskmanagement.config.SecurityConfig;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

@TestConfiguration
@Import({SecurityConfig.class, CorsConfig.class, SecurityProblemHandler.class,
        DatabaseJwtAuthenticationConverter.class, CurrentUser.class, JwtService.class,
        DemoAccountAccessPolicy.class})
public class SecurityTestConfig {
    // Public fixture key, used only by tests.
    public static final String SECRET_PROPERTY =
            "app.jwt.secret=dGVzdC1vbmx5LXNlY3JldC1hdC1sZWFzdC0zMi1ieXRlcy1sb25n";
}
