package com.taskmanagement.dto;

import com.taskmanagement.entity.TaskPriority;
import com.taskmanagement.entity.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record TaskRequest(
        @NotBlank @Size(max = 255) String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        Instant dueDate,
        @NotNull @Positive Long userId,
        @Positive Long categoryId,
        @Positive Long projectId) {
}
