package com.taskmanagement.security;

import java.util.Set;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Prevents seeded demo identities from authenticating outside the demo profile. */
@Component
public class DemoAccountAccessPolicy {
    private static final Set<String> DEMO_EMAILS = Set.of("phuc@example.com", "admin@example.com");

    private final Environment environment;

    public DemoAccountAccessPolicy(Environment environment) {
        this.environment = environment;
    }

    public boolean blocks(String email) {
        return DEMO_EMAILS.contains(email)
                && !environment.acceptsProfiles(Profiles.of("demo & !prod"));
    }
}
