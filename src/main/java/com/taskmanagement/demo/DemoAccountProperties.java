package com.taskmanagement.demo;

import com.taskmanagement.validation.Utf8Length;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.demo")
public record DemoAccountProperties(
        @NotBlank @Size(min = 8, max = 72) @Utf8Length(max = 72) String userPassword,
        @NotBlank @Size(min = 8, max = 72) @Utf8Length(max = 72) String adminPassword) {
    @Override
    public String toString() {
        return "DemoAccountProperties[passwords redacted]";
    }
}
