package com.taskmanagement.dto;

import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.TaskPriority;
import com.taskmanagement.entity.TaskStatus;
import java.time.Instant;

public record TaskSummaryResponse(
        Long id,
        String title,
        TaskStatus status,
        TaskPriority priority,
        Instant dueDate,
        Long categoryId) {

    public static TaskSummaryResponse fromEntity(Task task) {
        return new TaskSummaryResponse(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getCategory() != null ? task.getCategory().getId() : null);
    }
}