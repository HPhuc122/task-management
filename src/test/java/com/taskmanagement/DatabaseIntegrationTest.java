package com.taskmanagement;

import com.taskmanagement.entity.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import com.taskmanagement.security.SecurityTestConfig;
import org.springframework.test.context.TestPropertySource;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = SecurityTestConfig.SECRET_PROPERTY)
@Transactional
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class DatabaseIntegrationTest {
    @Autowired EntityManager entityManager;
    @Autowired JdbcTemplate jdbc;

    @Test
    void persistsEntitiesAndRefreshesDatabaseTimestamps() {
        User user = new User();
        user.setEmail("mapping@example.test");
        user.setPasswordHash("test-only-encoded-placeholder");
        entityManager.persist(user);
        Category category = new Category();
        category.setName("Work");
        entityManager.persist(category);
        Task task = new Task();
        task.setTitle("Verify database mapping");
        task.setUser(user);
        task.setCategory(category);
        entityManager.persist(task);
        entityManager.flush();
        assertNotNull(task.getCreatedAt());
        var before = task.getUpdatedAt();
        task.setStatus(TaskStatus.DONE);
        entityManager.flush();
        assertTrue(task.getUpdatedAt().isAfter(before));
        entityManager.clear();
        Task saved = entityManager.find(Task.class, task.getId());
        assertEquals(TaskStatus.DONE, saved.getStatus());
        assertEquals(TaskPriority.MEDIUM, saved.getPriority());
        assertEquals(user.getId(), saved.getUser().getId());
    }

    @Test
    void deletingCategoryKeepsTaskAndClearsCategory() {
        Long userId = insertUser();
        Long categoryId = jdbc.queryForObject("INSERT INTO categories(name) VALUES ('Work') RETURNING id", Long.class);
        Long taskId = jdbc.queryForObject("INSERT INTO tasks(title, user_id, category_id) VALUES ('Task', ?, ?) RETURNING id", Long.class, userId, categoryId);
        jdbc.update("DELETE FROM categories WHERE id = ?", categoryId);
        assertNull(jdbc.queryForObject("SELECT category_id FROM tasks WHERE id = ?", Long.class, taskId));
        assertEquals("TODO", jdbc.queryForObject("SELECT status FROM tasks WHERE id = ?", String.class, taskId));
    }

    @Test
    void rejectsDeletingOwnerWithTasks() {
        Long userId = insertUser();
        jdbc.update("INSERT INTO tasks(title, user_id) VALUES ('Task', ?)", userId);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM users WHERE id = ?", userId));
    }

    @Test
    void rejectsInvalidStatus() {
        Long userId = insertUser();
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update("INSERT INTO tasks(title, user_id, status) VALUES ('Task', ?, 'INVALID')", userId));
    }

    @Test
    void rejectsDuplicateEmail() {
        insertUser();
        assertThrows(DataIntegrityViolationException.class, this::insertUser);
    }

    private Long insertUser() {
        return jdbc.queryForObject("INSERT INTO users(email, password_hash) VALUES ('test@example.test', 'test-only-encoded-placeholder') RETURNING id", Long.class);
    }
}
