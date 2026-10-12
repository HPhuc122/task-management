package com.taskmanagement.dto;

import com.taskmanagement.entity.TaskAttachment;
import java.time.Instant;

public record TaskAttachmentResponse(
        Long id,
        Long taskId,
        String fileName,
        String contentType,
        long sizeBytes,
        String downloadUrl,
        Instant createdAt) {

    public static TaskAttachmentResponse from(TaskAttachment attachment) {
        Long taskId = attachment.getTask().getId();
        return new TaskAttachmentResponse(
                attachment.getId(), taskId, attachment.getFileName(), attachment.getContentType(),
                attachment.getSizeBytes(),
                "/api/tasks/" + taskId + "/attachments/" + attachment.getId() + "/content",
                attachment.getCreatedAt());
    }
}
