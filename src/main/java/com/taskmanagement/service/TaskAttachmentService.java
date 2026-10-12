package com.taskmanagement.service;

import com.taskmanagement.config.ObjectStorageProperties;
import com.taskmanagement.dto.TaskAttachmentResponse;
import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.TaskAttachment;
import com.taskmanagement.exception.AttachmentValidationException;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.repository.TaskAttachmentRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.security.CurrentUser;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(readOnly = true)
public class TaskAttachmentService {
    private final TaskRepository tasks;
    private final TaskAttachmentRepository attachments;
    private final ObjectStorageService storage;
    private final CurrentUser currentUser;
    private final long maxFileSize;

    public TaskAttachmentService(TaskRepository tasks, TaskAttachmentRepository attachments,
            ObjectStorageService storage, CurrentUser currentUser, ObjectStorageProperties properties) {
        this.tasks = tasks;
        this.attachments = attachments;
        this.storage = storage;
        this.currentUser = currentUser;
        this.maxFileSize = properties.maxFileSize().toBytes();
    }

    public List<TaskAttachmentResponse> list(Long taskId) {
        requireTask(taskId);
        return attachments.findAllByTaskIdOrderById(taskId).stream()
                .map(TaskAttachmentResponse::from).toList();
    }

    @Transactional
    public TaskAttachmentResponse upload(Long taskId, MultipartFile file) {
        Task task = requireTask(taskId);
        validate(file);
        String fileName = safeFileName(file.getOriginalFilename());
        String contentType = normalizeContentType(file.getContentType());
        String objectKey = "tasks/" + taskId + "/" + UUID.randomUUID();
        String etag;
        try {
            etag = storage.upload(objectKey, contentType, file.getSize(), file.getInputStream());
        } catch (IOException exception) {
            throw new AttachmentValidationException("Could not read the uploaded file");
        }

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTask(task);
        attachment.setObjectKey(objectKey);
        attachment.setFileName(fileName);
        attachment.setContentType(contentType);
        attachment.setSizeBytes(file.getSize());
        attachment.setEtag(etag);
        try {
            return TaskAttachmentResponse.from(attachments.saveAndFlush(attachment));
        } catch (RuntimeException exception) {
            storage.delete(objectKey);
            throw exception;
        }
    }

    public AttachmentContent download(Long taskId, Long attachmentId) {
        requireTask(taskId);
        TaskAttachment attachment = requireAttachment(taskId, attachmentId);
        return new AttachmentContent(attachment.getFileName(), attachment.getContentType(),
                attachment.getSizeBytes(), storage.download(attachment.getObjectKey()));
    }

    @Transactional
    public void delete(Long taskId, Long attachmentId) {
        requireTask(taskId);
        TaskAttachment attachment = requireAttachment(taskId, attachmentId);
        storage.delete(attachment.getObjectKey());
        attachments.delete(attachment);
        attachments.flush();
    }

    @Transactional
    public void deleteAllForTask(Task task) {
        List<TaskAttachment> rows = attachments.findAllByTaskIdOrderById(task.getId());
        rows.forEach(attachment -> storage.delete(attachment.getObjectKey()));
        attachments.deleteAll(rows);
        attachments.flush();
    }

    private Task requireTask(Long taskId) {
        Task task = tasks.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", taskId));
        currentUser.requireOwner(task.getUser().getId());
        return task;
    }

    private TaskAttachment requireAttachment(Long taskId, Long attachmentId) {
        return attachments.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task attachment", attachmentId));
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AttachmentValidationException("Attachment file must not be empty");
        }
        if (file.getSize() > maxFileSize) {
            throw new AttachmentValidationException("Attachment exceeds the maximum size of " + maxFileSize + " bytes");
        }
        safeFileName(file.getOriginalFilename());
    }

    private String safeFileName(String originalName) {
        if (!StringUtils.hasText(originalName)) {
            throw new AttachmentValidationException("Attachment file name must not be blank");
        }
        String cleaned = originalName.replace('\\', '/').replaceAll("[\\p{Cntrl}]", "");
        String name = cleaned.substring(cleaned.lastIndexOf('/') + 1).strip();
        if (name.isEmpty() || name.length() > 255) {
            throw new AttachmentValidationException("Attachment file name must contain at most 255 characters");
        }
        return name;
    }

    private String normalizeContentType(String value) {
        if (!StringUtils.hasText(value)) {
            return MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }
        try {
            String normalized = MediaType.parseMediaType(value).toString();
            if (normalized.length() > 255) {
                throw new AttachmentValidationException(
                        "Attachment content type must contain at most 255 characters");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new AttachmentValidationException("Attachment content type is invalid");
        }
    }

    public record AttachmentContent(String fileName, String contentType, long size, byte[] bytes) {}
}
