package com.taskmanagement;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.taskmanagement.dto.CategoryResponse;
import com.taskmanagement.security.SecurityTestConfig;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.cors.allowed-origins=http://localhost:3000", SecurityTestConfig.SECRET_PROPERTY})
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class FeatureIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired CacheManager cacheManager;

    @Test
    void categoryCacheRoundTripsRecordAndList() {
        Cache cache = cacheManager.getCache("categories");
        String key = "upgrade-" + UUID.randomUUID();
        CategoryResponse category = new CategoryResponse(42L, "Work", "Example");
        try {
            cache.put(key, category);
            cache.put(key + "-list", List.of(category));
            assertEquals(category, cache.get(key).get());
            assertEquals(List.of(category), cache.get(key + "-list").get());
        } finally {
            cache.evict(key);
            cache.evict(key + "-list");
        }
    }

    @Test
    @Transactional
    void cursorPaginationReturnsEachTaskOnce() throws Exception {
        Long userId = jdbc.queryForObject(
                "INSERT INTO users(email, password_hash) VALUES (?, ?) RETURNING id",
                Long.class, "cursor-" + UUID.randomUUID() + "@example.test", "test-only-placeholder");
        Long oldest = insertTask(userId, "oldest");
        Long middle = insertTask(userId, "middle");
        Long newest = insertTask(userId, "newest");

        JsonNode first = objectMapper.readTree(mvc.perform(get("/api/tasks")
                        .with(user(userId.toString()).roles("USER"))
                        .param("userId", userId.toString()).param("size", "2"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(newest.longValue(), first.path("content").get(0).path("id").asLong());
        assertEquals(middle.longValue(), first.path("content").get(1).path("id").asLong());
        assertTrue(first.path("hasNext").asBoolean());
        assertEquals(middle.longValue(), first.path("nextCursor").asLong());

        JsonNode second = objectMapper.readTree(mvc.perform(get("/api/tasks")
                        .with(user(userId.toString()).roles("USER"))
                        .param("userId", userId.toString()).param("size", "2")
                        .param("cursor", first.path("nextCursor").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertEquals(1, second.path("content").size());
        assertEquals(oldest.longValue(), second.path("content").get(0).path("id").asLong());
        assertFalse(second.path("hasNext").asBoolean());
        assertTrue(second.path("nextCursor").isNull());
    }

    @Test
    @Transactional
    void cursorPaginationRejectsAnotherUserAndInvalidParameters() throws Exception {
        Long ownerId = jdbc.queryForObject(
                "INSERT INTO users(email, password_hash) VALUES (?, ?) RETURNING id",
                Long.class, "owner-" + UUID.randomUUID() + "@example.test", "test-only-placeholder");

        mvc.perform(get("/api/tasks").with(user("999999").roles("USER"))
                        .param("userId", ownerId.toString()).param("size", "1"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/tasks").with(user(ownerId.toString()).roles("USER"))
                        .param("userId", ownerId.toString()).param("size", "0"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/tasks").with(user(ownerId.toString()).roles("USER"))
                        .param("userId", ownerId.toString()).param("cursor", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsPreflightAcceptsConfiguredOriginOnly() throws Exception {
        mvc.perform(options("/api/categories")
                        .header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:3000"));

        mvc.perform(options("/api/categories")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    private Long insertTask(Long userId, String title) {
        return jdbc.queryForObject(
                "INSERT INTO tasks(title, user_id) VALUES (?, ?) RETURNING id",
                Long.class, title, userId);
    }
}
