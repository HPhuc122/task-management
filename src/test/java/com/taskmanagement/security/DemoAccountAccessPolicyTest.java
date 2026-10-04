package com.taskmanagement.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoAccountAccessPolicyTest {
    @Test
    void demoCredentialsAreAcceptedOnlyInDemoWithoutProd() {
        assertTrue(policy("dev").blocks("phuc@example.com"));
        assertTrue(policy("prod").blocks("admin@example.com"));
        assertTrue(policy("demo", "prod").blocks("phuc@example.com"));
        assertFalse(policy("dev", "demo").blocks("phuc@example.com"));
        assertFalse(policy("dev", "demo").blocks("admin@example.com"));
        assertFalse(policy("prod").blocks("ordinary@example.com"));
    }

    private DemoAccountAccessPolicy policy(String... profiles) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profiles);
        return new DemoAccountAccessPolicy(environment);
    }
}
