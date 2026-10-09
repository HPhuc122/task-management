package com.taskmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.demo.DemoAccountSeeder;
import com.taskmanagement.security.SecurityTestConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "demo"})
@TestPropertySource(properties = {
        SecurityTestConfig.SECRET_PROPERTY,
        "app.demo.user-password=local-demo-user-pass",
        "app.demo.admin-password=local-demo-admin-pass"
})
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class DemoAccountIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DemoAccountSeeder seeder;

    @Test
    void seededAccountsCanLogInOnlyAfterDemoActivation() throws Exception {
        JsonNode user = login("phuc@example.com", "local-demo-user-pass");
        JsonNode admin = login("admin@example.com", "local-demo-admin-pass");
        assertEquals("USER", user.path("user").path("role").asText());
        assertEquals("ADMIN", admin.path("user").path("role").asText());

        String userHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'phuc@example.com'", String.class);
        String adminHash = jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'admin@example.com'", String.class);
        assertTrue(userHash.startsWith("$2"));
        assertTrue(adminHash.startsWith("$2"));
        assertNotEquals("local-demo-user-pass", userHash);

        mvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + user.path("accessToken").asText()))
                .andExpect(status().isOk());
        mvc.perform(get("/api/categories")
                        .header("Authorization", "Bearer " + admin.path("accessToken").asText()))
                .andExpect(status().isOk());

        seeder.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(userHash, jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'phuc@example.com'", String.class));
        assertEquals(adminHash, jdbc.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'admin@example.com'", String.class));
    }

    private JsonNode login(String email, String password) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/auth/login")
                        .contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }
}
