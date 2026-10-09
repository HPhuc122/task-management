package com.taskmanagement.notification;

import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryService {
    private final JdbcTemplate jdbc;
    private final JavaMailSender mail;
    private final String from;

    public EmailDeliveryService(JdbcTemplate jdbc, JavaMailSender mail,
                                @Value("${app.notifications.from}") String from) {
        this.jdbc = jdbc;
        this.mail = mail;
        this.from = from;
    }

    @Transactional
    public void deliver(UUID eventId) {
        var events = jdbc.query("""
                SELECT task_id, recipient, task_title, sent_at FROM notification_outbox
                WHERE event_id = ? FOR UPDATE
                """, (rs, row) -> new Email(rs.getLong("task_id"), rs.getString("recipient"),
                rs.getString("task_title"), rs.getTimestamp("sent_at") != null), eventId);
        if (events.isEmpty()) throw new AmqpRejectAndDontRequeueException("Unknown notification event");
        Email event = events.getFirst();
        if (event.sent()) return;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(event.recipient());
        message.setSubject("Task assigned: #" + event.taskId());
        message.setText("A task has been assigned to you.\n\nTask #" + event.taskId()
                + ": " + event.title() + "\n\nNotification ID: " + eventId);
        mail.send(message);
        // SMTP acceptance cannot be atomically committed with this DB write.
        jdbc.update("UPDATE notification_outbox SET sent_at = CURRENT_TIMESTAMP WHERE event_id = ?", eventId);
    }

    private record Email(long taskId, String recipient, String title, boolean sent) {}
}
