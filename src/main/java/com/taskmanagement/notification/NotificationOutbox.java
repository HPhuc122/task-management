package com.taskmanagement.notification;

import com.taskmanagement.entity.Task;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationOutbox {
    private final JdbcTemplate jdbc;

    public NotificationOutbox(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void taskCreated(Task task) {
        jdbc.update("""
                INSERT INTO notification_outbox(event_id, task_id, recipient, task_title)
                VALUES (?, ?, ?, ?)
                """, UUID.randomUUID(), task.getId(), task.getUser().getEmail(), task.getTitle());
    }
}
