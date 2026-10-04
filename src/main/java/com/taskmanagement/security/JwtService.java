package com.taskmanagement.security;

import com.taskmanagement.config.JwtProperties;
import com.taskmanagement.dto.AuthResponse;
import com.taskmanagement.dto.UserResponse;
import com.taskmanagement.entity.User;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AuthResponse issue(User user) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer()).subject(user.getId().toString())
                .issuedAt(now).expiresAt(now.plus(properties.accessTokenTtl()))
                .claim("role", user.getRole().name()).build();
        String token = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new AuthResponse(token, "Bearer", properties.accessTokenTtl().toSeconds(), UserResponse.from(user));
    }
}
