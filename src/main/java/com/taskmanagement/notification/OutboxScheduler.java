package com.taskmanagement.notification;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = {"app.notifications.enabled", "app.notifications.publisher-enabled"}, havingValue = "true")
public class OutboxScheduler {
    private final OutboxPublisher publisher;

    public OutboxScheduler(OutboxPublisher publisher) {
        this.publisher = publisher;
    }

    @Scheduled(fixedDelayString = "${app.notifications.poll-delay-ms:2000}")
    public void publish() {
        for (int i = 0; i < 20 && !Thread.currentThread().isInterrupted(); i++) {
            if (!publisher.publishNext()) break;
        }
    }
}
