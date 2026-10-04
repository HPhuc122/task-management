package com.taskmanagement.dto;

import com.taskmanagement.entity.Project;
import java.time.Instant;

public record ProjectResponse(
        Long id, String name, String description, Long userId,
        Instant createdAt, Instant updatedAt) {
    public static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getName(), project.getDescription(),
                project.getUser().getId(), project.getCreatedAt(), project.getUpdatedAt());
    }
}
