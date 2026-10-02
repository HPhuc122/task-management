package com.taskmanagement.dto;

import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.TaskPriority;
import com.taskmanagement.entity.TaskStatus;
import java.time.Instant;

public record TaskResponse(
        Long id, String title, String description, TaskStatus status, TaskPriority priority,
        Instant dueDate, Long userId, Long categoryId, Long projectId,
        Instant createdAt, Instant updatedAt) {
    public static TaskResponse from(Task task) {
        return new TaskResponse(task.getId(), task.getTitle(), task.getDescription(),
                task.getStatus(), task.getPriority(), task.getDueDate(), task.getUser().getId(),
                task.getCategory() == null ? null : task.getCategory().getId(),
                task.getProject() == null ? null : task.getProject().getId(),
                task.getCreatedAt(), task.getUpdatedAt());
    }
}
