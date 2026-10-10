package com.taskmanagement.config;

import org.springframework.amqp.core.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.notifications.enabled", havingValue = "true")
public class NotificationConfig {
    public static final String EXCHANGE = "task.notifications";
    public static final String QUEUE = "task.notifications.email";
    public static final String ROUTING_KEY = "task.created";
    public static final String DEAD_EXCHANGE = "task.notifications.dlx";
    public static final String DEAD_QUEUE = "task.notifications.email.dlq";
    public static final String DEAD_KEY = "email.failed";

    @Bean
    Declarables notificationTopology() {
        DirectExchange exchange = new DirectExchange(EXCHANGE);
        DirectExchange deadExchange = new DirectExchange(DEAD_EXCHANGE);
        Queue queue = QueueBuilder.durable(QUEUE).quorum()
                .deadLetterExchange(DEAD_EXCHANGE).deadLetterRoutingKey(DEAD_KEY)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish").build();
        Queue deadQueue = QueueBuilder.durable(DEAD_QUEUE).quorum().build();
        return new Declarables(exchange, deadExchange, queue, deadQueue,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(deadQueue).to(deadExchange).with(DEAD_KEY));
    }
}
