package com.taskmanagement;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.taskmanagement.security.SecurityTestConfig;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestPropertySource(properties = SecurityTestConfig.SECRET_PROPERTY)
@org.springframework.security.test.context.support.WithMockUser(username = "1", roles = "ADMIN")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class CrudIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    private Long userId;
    private Long categoryId;

    @BeforeEach
    void createOwner() {
        userId = jdbc.queryForObject("""
                INSERT INTO users(email, password_hash)
                VALUES (?, 'test-only-encoded-placeholder') RETURNING id
                """, Long.class, "crud-" + UUID.randomUUID() + "@example.test");
    }

    @AfterEach
    void deleteFixtures() {
        jdbc.update("DELETE FROM tasks WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM projects WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
        if (categoryId != null) {
            jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
        }
    }

    @Test
    void projectCrudUpdatesDatabaseTimestampsAndReturnsSafeDtos() throws Exception {
        JsonNode created = createProject();
        long id = created.get("id").asLong();
        assertFalse(created.has("user"));
        assertFalse(created.has("passwordHash"));
        assertNotNull(created.get("createdAt"));
        mvc.perform(get("/api/projects/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Project"));
        mvc.perform(get("/api/projects")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));

        JsonNode updated = body(mvc.perform(put("/api/projects/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Updated", "userId", userId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").isEmpty()).andReturn());
        assertEquals(created.get("createdAt"), updated.get("createdAt"));
        assertTrue(Instant.parse(updated.get("updatedAt").asText())
                .isAfter(Instant.parse(created.get("updatedAt").asText())));
        assertEquals("Updated", jdbc.queryForObject("SELECT name FROM projects WHERE id = ?", String.class, id));

        mvc.perform(delete("/api/projects/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/projects/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void taskCrudPersistsRelationsAndPutClearsOptionalFields() throws Exception {
        long projectId = createProject().get("id").asLong();
        categoryId = jdbc.queryForObject("INSERT INTO categories(name) VALUES (?) RETURNING id",
                Long.class, "CRUD-" + UUID.randomUUID());
        JsonNode created = body(mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("title", " Task ", "userId", userId,
                                "categoryId", categoryId, "projectId", projectId,
                                "description", "Details", "dueDate", "2027-01-01T00:00:00Z"))))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.title").value("Task"))
                .andExpect(jsonPath("$.status").value("TODO"))
                .andExpect(jsonPath("$.priority").value("MEDIUM"))
                .andExpect(jsonPath("$.projectId").value(projectId))
                .andExpect(jsonPath("$.categoryId").value(categoryId))
                .andExpect(jsonPath("$.user").doesNotExist()).andReturn());
        long id = created.get("id").asLong();
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2027-01-01T00:00:00Z"));
        mvc.perform(get("/api/tasks")).andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem((int) id)));

        JsonNode updated = body(mvc.perform(put("/api/tasks/{id}", id).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("title", "Updated task", "userId", userId,
                                "status", "DONE", "priority", "HIGH"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"))
                .andExpect(jsonPath("$.priority").value("HIGH"))
                .andExpect(jsonPath("$.categoryId").isEmpty()).andExpect(jsonPath("$.projectId").isEmpty())
                .andExpect(jsonPath("$.description").isEmpty()).andExpect(jsonPath("$.dueDate").isEmpty())
                .andReturn());
        assertTrue(Instant.parse(updated.get("updatedAt").asText())
                .isAfter(Instant.parse(created.get("updatedAt").asText())));
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, id));
        mvc.perform(delete("/api/tasks/{id}", id)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void deletingProjectPreservesTaskAndClearsRelation() throws Exception {
        long projectId = createProject().get("id").asLong();
        long taskId = body(mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("title", "Keep task", "userId", userId,
                                "projectId", projectId))))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
        mvc.perform(delete("/api/projects/{id}", projectId)).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/{id}", taskId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Keep task"))
                .andExpect(jsonPath("$.projectId").isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"userId", "categoryId", "projectId"})
    void rejectsMissingTaskReferences(String field) throws Exception {
        var request = new java.util.HashMap<String, Object>();
        request.put("title", "Invalid reference");
        request.put("userId", userId);
        request.put(field, Long.MAX_VALUE);
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM tasks WHERE title = 'Invalid reference'", Integer.class));
    }

    @Test
    void rejectsMissingProjectOwner() throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Invalid project", "userId", Long.MAX_VALUE))))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasks", "projects"})
    void updatingMissingResourceReturnsNotFound(String resource) throws Exception {
        String field = resource.equals("tasks") ? "title" : "name";
        mvc.perform(put("/api/" + resource + "/" + Long.MAX_VALUE).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of(field, "Missing", "userId", userId))))
                .andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tasks", "projects"})
    void deletingMissingResourceReturnsNotFound(String resource) throws Exception {
        mvc.perform(delete("/api/" + resource + "/" + Long.MAX_VALUE)).andExpect(status().isNotFound());
    }

    @Test
    void rejectsDeletingProjectOwner() throws Exception {
        createProject();
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM users WHERE id = ?", userId));
    }

    private JsonNode createProject() throws Exception {
        return body(mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", " Project ", "description", "Details", "userId", userId))))
                .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn());
    }

    private JsonNode body(MvcResult result) throws Exception {
        JsonNode body = mapper.readTree(result.getResponse().getContentAsString());
        if (result.getResponse().getStatus() == 201) {
            assertTrue(result.getResponse().getHeader("Location").endsWith("/" + body.get("id").asLong()));
        }
        return body;
    }
}
