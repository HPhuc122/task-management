package com.taskmanagement.controller;

import com.taskmanagement.dto.TaskAttachmentResponse;
import com.taskmanagement.service.TaskAttachmentService;
import com.taskmanagement.service.TaskAttachmentService.AttachmentContent;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tasks/{taskId}/attachments")
public class TaskAttachmentController {
    private final TaskAttachmentService service;

    public TaskAttachmentController(TaskAttachmentService service) {
        this.service = service;
    }

    @GetMapping
    public List<TaskAttachmentResponse> list(@PathVariable @Positive Long taskId) {
        return service.list(taskId);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TaskAttachmentResponse> upload(
            @PathVariable @Positive Long taskId, @RequestParam("file") MultipartFile file) {
        TaskAttachmentResponse response = service.upload(taskId, file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{attachmentId}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{attachmentId}/content")
    public ResponseEntity<byte[]> download(@PathVariable @Positive Long taskId,
            @PathVariable @Positive Long attachmentId) {
        AttachmentContent content = service.download(taskId, attachmentId);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(content.contentType());
        } catch (IllegalArgumentException ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok()
                .contentType(mediaType)
                .contentLength(content.bytes().length)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(content.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(content.bytes());
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long taskId,
            @PathVariable @Positive Long attachmentId) {
        service.delete(taskId, attachmentId);
        return ResponseEntity.noContent().build();
    }
}
