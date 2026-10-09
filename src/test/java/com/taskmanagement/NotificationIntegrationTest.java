package com.taskmanagement;

import com.taskmanagement.notification.EmailDeliveryService;
import com.taskmanagement.notification.OutboxPublisher;
import com.taskmanagement.security.SecurityTestConfig;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@TestPropertySource(properties = {SecurityTestConfig.SECRET_PROPERTY, "app.notifications.enabled=true",
        "app.notifications.publisher-enabled=false", "spring.rabbitmq.listener.simple.auto-startup=false"})
@EnabledIfEnvironmentVariable(named = "RUN_DATABASE_TESTS", matches = "true")
class NotificationIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EmailDeliveryService delivery;
    @Autowired OutboxPublisher publisher;
    @MockitoBean JavaMailSender mail;
    @MockitoBean RabbitTemplate rabbit;
    private UUID event;

    @BeforeEach
    void setup() {
        event = UUID.randomUUID();
        // Sort our fixture before unrelated history left by other tests.
        jdbc.update("""
                INSERT INTO notification_outbox(event_id,task_id,recipient,task_title,next_attempt_at)
                VALUES (?, 42, 'owner@example.test', 'Test task', '1900-01-01T00:00:00Z')
                """, event);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM notification_outbox WHERE event_id = ?", event);
    }

    @Test
    void concurrentDuplicateDeliverySendsOneEmail() throws Exception {
        try (var pool = Executors.newFixedThreadPool(2)) {
            CountDownLatch start = new CountDownLatch(1);
            Runnable receive = () -> {
                try { start.await(); } catch (InterruptedException exception) { throw new RuntimeException(exception); }
                delivery.deliver(event);
            };
            var first = pool.submit(receive);
            var second = pool.submit(receive);
            start.countDown();
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }
        delivery.deliver(event);
        verify(mail, times(1)).send(argThat((SimpleMailMessage message) ->
                message.getTo()[0].equals("owner@example.test") && message.getText().contains(event.toString())));
        assertTrue(sent());
    }

    @Test
    void smtpFailureRollsBackReceiptAndAllowsRetry() {
        doThrow(new MailSendException("Test SMTP failure")).doNothing().when(mail).send(any(SimpleMailMessage.class));
        assertThrows(MailSendException.class, () -> delivery.deliver(event));
        assertFalse(sent());
        delivery.deliver(event);
        assertTrue(sent());
        verify(mail, times(2)).send(any(SimpleMailMessage.class));
    }

    @Test
    void confirmedPublishMarksOutboxAndPreservesStableEventId() {
        doAnswer(call -> {
            Message message = call.getArgument(2);
            assertEquals(event.toString(), message.getMessageProperties().getMessageId());
            assertEquals(MessageDeliveryMode.PERSISTENT, message.getMessageProperties().getDeliveryMode());
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        assertTrue(publisher.publishNext());
        assertNotNull(jdbc.queryForObject("SELECT published_at FROM notification_outbox WHERE event_id = ?", java.sql.Timestamp.class, event));
        verify(rabbit, times(1)).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
    }

    @Test
    void nackOrUnroutablePublishRemainsPendingForRetry() {
        doAnswer(call -> {
            ((CorrelationData) call.getArgument(3)).getFuture().complete(new CorrelationData.Confirm(false, "Test NACK"));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishNext();
        assertPending(1);
        jdbc.update("UPDATE notification_outbox SET next_attempt_at = '1900-01-01T00:00:00Z' WHERE event_id = ?", event);
        doAnswer(call -> {
            CorrelationData correlation = call.getArgument(3);
            correlation.setReturned(new ReturnedMessage(call.getArgument(2), 312, "NO_ROUTE", "test", "test"));
            correlation.getFuture().complete(new CorrelationData.Confirm(true, null));
            return null;
        }).when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishNext();
        assertPending(2);
    }

    @Test
    void brokerFailureDoesNotLoseOutbox() {
        doThrow(new org.springframework.amqp.AmqpConnectException(new java.net.ConnectException()))
                .when(rabbit).send(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        publisher.publishNext();
        assertPending(1);
    }

    private boolean sent() {
        return jdbc.queryForObject("SELECT sent_at IS NOT NULL FROM notification_outbox WHERE event_id = ?", Boolean.class, event);
    }

    private void assertPending(int attempts) {
        assertNull(jdbc.queryForObject("SELECT published_at FROM notification_outbox WHERE event_id = ?", java.sql.Timestamp.class, event));
        assertEquals(attempts, jdbc.queryForObject("SELECT attempts FROM notification_outbox WHERE event_id = ?", Integer.class, event));
        assertTrue(jdbc.queryForObject("SELECT next_attempt_at > CURRENT_TIMESTAMP FROM notification_outbox WHERE event_id = ?", Boolean.class, event));
    }
}
