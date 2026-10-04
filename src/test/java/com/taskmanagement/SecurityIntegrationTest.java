package com.taskmanagement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.security.SecurityTestConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = SecurityTestConfig.SECRET_PROPERTY)
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class SecurityIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    private final List<Long> userIds = new ArrayList<>();
    private Account alice;
    private Account bob;

    @BeforeEach
    void registerUsers() throws Exception {
        alice = register();
        bob = register();
    }

    @AfterEach
    void cleanup() {
        for (Long id : userIds) jdbc.update("DELETE FROM tasks WHERE user_id = ?", id);
        for (Long id : userIds) jdbc.update("DELETE FROM projects WHERE user_id = ?", id);
        for (Long id : userIds) jdbc.update("DELETE FROM users WHERE id = ?", id);
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasks", "projects"})
    void enforcesOwnershipForEveryCrudOperationAndAdminCanManageAll(String resource) throws Exception {
        String path = "/api/" + resource;
        String field = resource.equals("tasks") ? "title" : "name";
        Map<String, Object> aliceBody = Map.of(field, "Alice resource", "userId", alice.id());
        Map<String, Object> bobBody = Map.of(field, "Bob resource", "userId", bob.id());
        long aliceResource = json(perform(post(path), alice, aliceBody).andExpect(status().isCreated())).get("id").asLong();
        long bobResource = json(perform(post(path), bob, bobBody).andExpect(status().isCreated())).get("id").asLong();
        perform(get(path), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))).andExpect(jsonPath("$[0].id").value(aliceResource));
        perform(get(path + "/" + aliceResource), alice, null).andExpect(status().isOk());
        perform(get(path + "/" + bobResource), alice, null).andExpect(status().isForbidden());
        perform(put(path + "/" + bobResource), alice, aliceBody).andExpect(status().isForbidden());
        perform(delete(path + "/" + bobResource), alice, null).andExpect(status().isForbidden());
        perform(post(path), alice, bobBody).andExpect(status().isForbidden());
        perform(put(path + "/" + aliceResource), alice, bobBody).andExpect(status().isForbidden());
        assertEquals(bob.id(), jdbc.queryForObject("SELECT user_id FROM " + resource + " WHERE id = ?", Long.class, bobResource));
        perform(put(path + "/" + aliceResource), alice, aliceBody).andExpect(status().isOk());
        perform(delete(path + "/" + aliceResource), alice, null).andExpect(status().isNoContent());

        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", alice.id());
        // The same token immediately receives the current DB role.
        perform(get(path), alice, null).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) bobResource)));
        perform(get(path + "/" + bobResource), alice, null).andExpect(status().isOk());
        perform(put(path + "/" + bobResource), alice, aliceBody).andExpect(status().isOk());
        perform(post(path), alice, bobBody).andExpect(status().isCreated());
        perform(delete(path + "/" + bobResource), alice, null).andExpect(status().isNoContent());
    }

    @Test
    void forbidsForeignProjectReferenceAndRollsBackRejectedUpdate() throws Exception {
        long project = json(perform(post("/api/projects"), bob,
                Map.of("name", "Bob project", "userId", bob.id())).andExpect(status().isCreated())).get("id").asLong();
        Map<String, Object> foreignProject = Map.of("title", "Forbidden", "userId", alice.id(), "projectId", project);
        perform(post("/api/tasks"), alice, foreignProject).andExpect(status().isForbidden());
        long task = json(perform(post("/api/tasks"), alice,
                Map.of("title", "Original", "userId", alice.id())).andExpect(status().isCreated())).get("id").asLong();
        perform(put("/api/tasks/" + task), alice, foreignProject).andExpect(status().isForbidden());
        assertNull(jdbc.queryForObject("SELECT project_id FROM tasks WHERE id = ?", Long.class, task));
        assertEquals("Original", jdbc.queryForObject("SELECT title FROM tasks WHERE id = ?", String.class, task));
        jdbc.update("UPDATE users SET role = 'ADMIN' WHERE id = ?", alice.id());
        perform(put("/api/tasks/" + task), alice, foreignProject).andExpect(status().isOk());
    }

    @Test
    void persistsEncodedPasswordLogsInRejectsDuplicatesAndDeletedUsers() throws Exception {
        String hash = jdbc.queryForObject("SELECT password_hash FROM users WHERE id = ?", String.class, alice.id());
        assertTrue(passwords.matches("password123", hash));
        perform(post("/api/auth/login"), null, Map.of("email", alice.email(), "password", "password123"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isString());
        perform(post("/api/auth/register"), null, Map.of("email", alice.email(), "password", "password123"))
                .andExpect(status().isConflict());
        perform(post("/api/auth/login"), null, Map.of("email", alice.email(), "password", "wrong-password"))
                .andExpect(status().isUnauthorized());
        perform(post("/api/auth/login"), null, Map.of("email", "admin@example.com", "password", "demo_password_hash_3"))
                .andExpect(status().isUnauthorized());
        jdbc.update("DELETE FROM users WHERE id = ?", alice.id());
        perform(get("/api/auth/me"), alice, null).andExpect(status().isUnauthorized());
    }

    private Account register() throws Exception {
        String email = "security-" + UUID.randomUUID() + "@example.test";
        JsonNode body = json(perform(post("/api/auth/register"), null,
                Map.of("email", email, "password", "password123", "role", "ADMIN"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.user.role").value("USER")));
        long id = body.get("user").get("id").asLong();
        userIds.add(id);
        return new Account(id, email, body.get("accessToken").asText());
    }

    private ResultActions perform(MockHttpServletRequestBuilder request, Account account, Object body) throws Exception {
        if (account != null) request.header("Authorization", "Bearer " + account.token());
        if (body != null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }

    private JsonNode json(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private record Account(long id, String email, String token) {}
}
