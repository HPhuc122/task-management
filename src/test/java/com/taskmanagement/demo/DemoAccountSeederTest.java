package com.taskmanagement.demo;

import com.taskmanagement.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DemoAccountSeederTest {
    @Test
    void invalidPasswordFailsBeforeDatabaseWriteWithoutLeakingItsValue() {
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        DemoAccountSeeder seeder = new DemoAccountSeeder(users, passwords,
                new DemoAccountProperties("short", "local-demo-admin-pass"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> seeder.run(new DefaultApplicationArguments(new String[0])));
        assertFalse(error.getMessage().contains("short"));
        verifyNoInteractions(users, passwords);
    }
}
