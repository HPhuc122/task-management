package com.taskmanagement.notification;

import com.taskmanagement.config.NotificationConfig;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class OutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;

    public OutboxPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
    }

    @Transactional
    public boolean publishNext() {
        var pending = jdbc.query("""
                SELECT event_id, attempts FROM notification_outbox
                WHERE published_at IS NULL AND next_attempt_at <= CURRENT_TIMESTAMP
                ORDER BY next_attempt_at, created_at LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (rs, row) -> new Pending(rs.getObject("event_id", UUID.class), rs.getInt("attempts")));
        if (pending.isEmpty()) return false;
        Pending event = pending.getFirst();
        try {
            CorrelationData correlation = new CorrelationData(UUID.randomUUID().toString());
            var message = MessageBuilder.withBody(event.id().toString().getBytes(StandardCharsets.UTF_8))
                    .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                    .setContentEncoding("UTF-8").setMessageId(event.id().toString())
                    .setDeliveryMode(MessageDeliveryMode.PERSISTENT).build();
            rabbit.send(NotificationConfig.EXCHANGE, NotificationConfig.ROUTING_KEY, message, correlation);
            var confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlation.getReturned() != null) {
                throw new IllegalStateException("Notification publish was not routed and confirmed");
            }
            jdbc.update("UPDATE notification_outbox SET published_at = CURRENT_TIMESTAMP WHERE event_id = ?", event.id());
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) Thread.currentThread().interrupt();
            long delay = Math.min(300, 1L << Math.min(event.attempts() + 1, 9));
            jdbc.update("""
                    UPDATE notification_outbox SET attempts = attempts + 1,
                        next_attempt_at = CURRENT_TIMESTAMP + (? * INTERVAL '1 second')
                    WHERE event_id = ?
                    """, delay, event.id());
            // No recipient, body, credentials or raw broker exception in logs.
            log.warn("Notification event {} publish failed; retry in {} seconds", event.id(), delay);
        }
        return true;
    }

    private record Pending(UUID id, int attempts) {}
}
