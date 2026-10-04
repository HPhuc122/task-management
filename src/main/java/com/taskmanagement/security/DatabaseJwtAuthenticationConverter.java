package com.taskmanagement.security;

import com.taskmanagement.repository.UserRepository;
import java.util.List;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class DatabaseJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {
    private final UserRepository users;
    private final DemoAccountAccessPolicy demoAccounts;

    public DatabaseJwtAuthenticationConverter(UserRepository users, DemoAccountAccessPolicy demoAccounts) {
        this.users = users;
        this.demoAccounts = demoAccounts;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        long id;
        try {
            id = Long.parseLong(jwt.getSubject());
        } catch (NumberFormatException exception) {
            throw new InvalidBearerTokenException("Invalid subject");
        }
        var user = users.findById(id)
                .orElseThrow(() -> new InvalidBearerTokenException("User no longer exists"));
        if (demoAccounts.blocks(user.getEmail())) {
            throw new InvalidBearerTokenException("Demo account is disabled");
        }
        return new JwtAuthenticationToken(jwt,
                List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())), Long.toString(id));
    }
}
