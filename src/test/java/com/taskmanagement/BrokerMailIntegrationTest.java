package com.taskmanagement;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.taskmanagement.config.NotificationConfig;
import com.taskmanagement.notification.OutboxPublisher;
import com.taskmanagement.security.SecurityTestConfig;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Run only against disposable PostgreSQL, RabbitMQ vhost and local Mailpit. */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {SecurityTestConfig.SECRET_PROPERTY, "app.notifications.enabled=true",
        "app.notifications.publisher-enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.max-interval=200ms"})
@EnabledIfEnvironmentVariable(named = "RUN_NOTIFICATION_TESTS", matches = "true")
class BrokerMailIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired OutboxPublisher publisher;
    @Autowired RabbitTemplate rabbit;
    @Autowired AmqpAdmin admin;
    @Autowired RabbitListenerEndpointRegistry listeners;
    @Value("${MAILPIT_API:http://localhost:8025}") String mailpit;
    private Long actor;
    private UUID event;

    @BeforeEach
    void setup() {
        listeners.stop();
        // Establish connection: RabbitAdmin auto-declares topology on connection creation.
        assertNotNull(admin.getQueueProperties(NotificationConfig.QUEUE));
        admin.purgeQueue(NotificationConfig.QUEUE);
        admin.purgeQueue(NotificationConfig.DEAD_QUEUE);
        actor = jdbc.queryForObject("INSERT INTO users(email,password_hash) VALUES (?, 'test') RETURNING id",
                Long.class, "mail-" + UUID.randomUUID() + "@example.test");
    }

    @AfterEach
    void cleanup() {
        listeners.stop();
        admin.purgeQueue(NotificationConfig.QUEUE);
        admin.purgeQueue(NotificationConfig.DEAD_QUEUE);
        if (event != null) jdbc.update("DELETE FROM notification_outbox WHERE event_id = ?", event);
        jdbc.update("DELETE FROM tasks WHERE user_id = ?", actor);
        jdbc.update("DELETE FROM api_idempotency WHERE actor_id = ?", actor);
        jdbc.update("DELETE FROM users WHERE id = ?", actor);
    }

    @Test
    void httpTaskToOutboxToRabbitToSmtpAndRedeliveryProducesOneMail() throws Exception {
        String key = UUID.randomUUID().toString();
        String body = "{\"title\":\"RabbitMQ notification test\",\"userId\":" + actor + "}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/tasks").with(user(actor.toString()).roles("USER"))
                    .header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }
        event = jdbc.queryForObject("""
                SELECT event_id FROM notification_outbox WHERE task_id IN
                (SELECT id FROM tasks WHERE user_id = ?)
                """, UUID.class, actor);
        // Prioritize our fixture without changing other tests' outbox history.
        jdbc.update("UPDATE notification_outbox SET next_attempt_at = '1900-01-01T00:00:00Z' WHERE event_id = ?", event);
        assertTrue(publisher.publishNext());
        listeners.start();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertTrue(jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM notification_outbox WHERE event_id = ?", Boolean.class, event)));
        listeners.stop();
        rabbit.convertAndSend(NotificationConfig.EXCHANGE, NotificationConfig.ROUTING_KEY, event.toString());
        rabbit.convertAndSend(NotificationConfig.EXCHANGE, NotificationConfig.ROUTING_KEY, event.toString());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertEquals(2, queueSize(NotificationConfig.QUEUE)));
        listeners.start();
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertEquals(0, queueSize(NotificationConfig.QUEUE)));
        listeners.stop();
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            JsonNode search = searchMail(event);
            assertEquals(1, search.path("messages").size());
            assertEquals("mail-", search.path("messages").get(0).path("To").get(0).path("Address").asText().substring(0, 5));
        });
    }

    @Test
    void invalidEventIsDeadLetteredAndCanBeReplayedAfterRepair() {
        event = UUID.randomUUID();
        rabbit.convertAndSend(NotificationConfig.EXCHANGE, NotificationConfig.ROUTING_KEY, event.toString());
        listeners.start();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> assertEquals(1, queueSize(NotificationConfig.DEAD_QUEUE)));
        listeners.stop();
        assertNull(jdbc.queryForObject("SELECT max(sent_at) FROM notification_outbox WHERE event_id = ?", java.sql.Timestamp.class, event));
        jdbc.update("""
                INSERT INTO notification_outbox(event_id,task_id,recipient,task_title,published_at)
                VALUES (?, 123, 'replay@example.test', 'Recovered event', CURRENT_TIMESTAMP)
                """, event);
        var failed = rabbit.receive(NotificationConfig.DEAD_QUEUE, 5000);
        assertNotNull(failed);
        assertEquals(event.toString(), new String(failed.getBody(), java.nio.charset.StandardCharsets.UTF_8));
        rabbit.send(NotificationConfig.EXCHANGE, NotificationConfig.ROUTING_KEY, failed);
        listeners.start();
        await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                assertTrue(jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM notification_outbox WHERE event_id = ?", Boolean.class, event)));
    }

    private int queueSize(String queue) {
        return ((Number) admin.getQueueProperties(queue).get("QUEUE_MESSAGE_COUNT")).intValue();
    }

    private JsonNode searchMail(UUID id) throws Exception {
        try (var http = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(mailpit + "/api/v1/search?query=" + id))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            return mapper.readTree(response.body());
        }
    }
}
