package com.taskmanagement.demo;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.demo")
public record DemoAccountProperties(
        String userPassword,
        String adminPassword) {
    @Override
    public String toString() {
        return "DemoAccountProperties[passwords redacted]";
    }
}
