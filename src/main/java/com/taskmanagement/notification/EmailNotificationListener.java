package com.taskmanagement.notification;

import com.taskmanagement.config.NotificationConfig;
import java.util.UUID;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class EmailNotificationListener {
    private final EmailDeliveryService delivery;

    public EmailNotificationListener(EmailDeliveryService delivery) {
        this.delivery = delivery;
    }

    @RabbitListener(queues = NotificationConfig.QUEUE)
    public void receive(String body) {
        UUID eventId;
        try {
            eventId = UUID.fromString(body);
        } catch (IllegalArgumentException exception) {
            throw new AmqpRejectAndDontRequeueException("Invalid notification event ID");
        }
        delivery.deliver(eventId);
    }
}
