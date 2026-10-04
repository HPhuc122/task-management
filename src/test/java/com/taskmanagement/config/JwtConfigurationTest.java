package com.taskmanagement.config;

import jakarta.validation.Validation;
import java.time.Duration;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;

class JwtConfigurationTest {
    @ParameterizedTest
    @ValueSource(strings = {"", "not base64!", "c2hvcnQ="})
    void rejectsEmptyMalformedAndWeakSecrets(String secret) {
        var properties = new JwtProperties(secret, "task-management", Duration.ofHours(1));
        assertThrows(IllegalStateException.class, () -> new SecurityConfig().jwtSecretKey(properties));
    }

    @Test
    void accepts256BitKeyWithoutExposingItInToString() {
        String secret = Base64.getEncoder().encodeToString(new byte[32]);
        var properties = new JwtProperties(secret, "task-management", Duration.ofHours(1));
        assertEquals(32, new SecurityConfig().jwtSecretKey(properties).getEncoded().length);
        assertFalse(properties.toString().contains(secret));
    }

    @Test
    void missingSecretFailsConfigurationBinding() {
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfig.class)
                .withPropertyValues("app.jwt.issuer=task-management", "app.jwt.access-token-ttl=1h")
                .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Test
    void rejectsZeroNegativeAndSubSecondLifetime() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (Duration ttl : new Duration[]{Duration.ZERO, Duration.ofSeconds(-1), Duration.ofMillis(500)}) {
                assertFalse(validator.validate(new JwtProperties("secret", "task-management", ttl)).isEmpty());
            }
            assertTrue(validator.validate(new JwtProperties("secret", "task-management", Duration.ofSeconds(1))).isEmpty());
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesConfig {}
}
