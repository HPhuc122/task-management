package com.taskmanagement;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.taskmanagement.security.SecurityTestConfig;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {SecurityTestConfig.SECRET_PROPERTY, "app.notifications.enabled=false"})
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class IdempotencyIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private Long actor;
    private String key;

    @BeforeEach
    void setup() {
        actor = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?, 'test') RETURNING id",
                Long.class, "idempotency-" + UUID.randomUUID() + "@example.test");
        key = UUID.randomUUID().toString();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM notification_outbox WHERE task_id IN (SELECT id FROM tasks WHERE user_id = ?)", actor);
        jdbc.update("DELETE FROM tasks WHERE user_id = ?", actor);
        jdbc.update("DELETE FROM api_idempotency WHERE actor_id = ?", actor);
        jdbc.update("DELETE FROM users WHERE id = ?", actor);
    }

    @Test
    void replaysSame201BodyAndLocationButRejectsDifferentPayload() throws Exception {
        var first = create(actor, key, body("First"));
        var second = create(actor, key, "{\"userId\":" + actor + ",\"title\":\"First\"}");
        assertEquals(201, first.getResponse().getStatus());
        assertEquals(201, second.getResponse().getStatus());
        assertEquals(first.getResponse().getContentAsString(), second.getResponse().getContentAsString());
        assertEquals(first.getResponse().getHeader("Location"), second.getResponse().getHeader("Location"));
        assertEquals(409, create(actor, key, body("Changed")).getResponse().getStatus());
        assertCounts(1, 1, 1);
    }

    @Test
    void concurrentRetriesCreateOneTaskAndOneOutboxEvent() throws Exception {
        try (var pool = Executors.newFixedThreadPool(4)) {
            CountDownLatch start = new CountDownLatch(1);
            Callable<String> retry = () -> {
                start.await();
                var response = create(actor, key, body("Concurrent")).getResponse();
                assertEquals(201, response.getStatus());
                return response.getContentAsString();
            };
            var results = List.of(pool.submit(retry), pool.submit(retry), pool.submit(retry), pool.submit(retry));
            start.countDown();
            String expected = results.getFirst().get(15, TimeUnit.SECONDS);
            for (var result : results) assertEquals(expected, result.get(15, TimeUnit.SECONDS));
        }
        assertCounts(1, 1, 1);
    }

    @Test
    void failedRequestDoesNotReserveKeyAndTransactionRollbackRemovesTaskAndOutbox() throws Exception {
        String invalid = "{\"title\":\"Bad reference\",\"userId\":" + actor + ",\"categoryId\":9223372036854775807}";
        assertEquals(404, create(actor, key, invalid).getResponse().getStatus());
        assertCounts(0, 0, 0);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            try {
                assertEquals(201, create(actor, key, body("Rollback")).getResponse().getStatus());
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            }
            status.setRollbackOnly();
        });
        assertCounts(0, 0, 0);
        assertEquals(201, create(actor, key, body("Retry after rollback")).getResponse().getStatus());
        assertCounts(1, 1, 1);
    }

    @Test
    void keyScopeIsPerActorAndNoKeyRetainsOriginalCreateBehavior() throws Exception {
        assertEquals(201, create(actor, key, body("Scoped")).getResponse().getStatus());
        // ADMIN can create for the same owner using the same key, scoped to a different actor.
        try {
            assertEquals(201, mvc.perform(post("/api/tasks").with(user("900000000").roles("ADMIN"))
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body("Scoped")))
                    .andReturn().getResponse().getStatus());
        } finally {
            jdbc.update("DELETE FROM api_idempotency WHERE actor_id = 900000000 AND request_key = ?", key);
        }
        assertEquals(201, create(actor, null, body("No key")).getResponse().getStatus());
        assertEquals(201, create(actor, null, body("No key")).getResponse().getStatus());
        assertCounts(4, 1, 4);
    }

    @Test
    void replayChecksCurrentOwnershipAndInvalidKeyIsRejected() throws Exception {
        for (String invalid : List.of("", "bad key", "a".repeat(129))) {
            assertEquals(400, create(actor, invalid, body("Invalid key")).getResponse().getStatus());
        }
        assertCounts(0, 0, 0);
        var created = create(actor, key, body("Owned"));
        long id = mapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        Long other = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?, 'test') RETURNING id",
                Long.class, UUID.randomUUID() + "@example.test");
        try {
            jdbc.update("UPDATE tasks SET user_id = ? WHERE id = ?", other, id);
            assertEquals(403, create(actor, key, body("Owned")).getResponse().getStatus());
        } finally {
            jdbc.update("UPDATE tasks SET user_id = ? WHERE id = ?", actor, id);
            jdbc.update("DELETE FROM users WHERE id = ?", other);
        }
    }

    private MvcResult create(Long who, String requestKey, String body) throws Exception {
        var request = post("/api/tasks").with(user(who.toString()).roles("USER"))
                .contentType(MediaType.APPLICATION_JSON).content(body);
        if (requestKey != null) request.header("Idempotency-Key", requestKey);
        return mvc.perform(request).andReturn();
    }

    private String body(String title) {
        return "{\"title\":\"" + title + "\",\"userId\":" + actor + "}";
    }

    private void assertCounts(int tasks, int keys, int events) {
        assertEquals(tasks, jdbc.queryForObject("SELECT count(*) FROM tasks WHERE user_id = ?", Integer.class, actor));
        assertEquals(keys, jdbc.queryForObject("SELECT count(*) FROM api_idempotency WHERE actor_id = ?", Integer.class, actor));
        assertEquals(events, jdbc.queryForObject("SELECT count(*) FROM notification_outbox WHERE task_id IN (SELECT id FROM tasks WHERE user_id = ?)", Integer.class, actor));
    }
}
