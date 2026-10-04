package com.taskmanagement.config;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(@NotBlank String secret, @NotBlank String issuer,
                            @NotNull Duration accessTokenTtl) {
    @AssertTrue(message = "access-token-ttl must be at least one second")
    public boolean isTtlValid() {
        return accessTokenTtl != null && accessTokenTtl.compareTo(Duration.ofSeconds(1)) >= 0;
    }

    @Override
    public String toString() {
        return "JwtProperties[secret redacted]";
    }
}
