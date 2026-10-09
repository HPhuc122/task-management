package com.taskmanagement.controller;

import com.taskmanagement.dto.CursorPage;
import com.taskmanagement.dto.TaskSummaryResponse;
import com.taskmanagement.dto.TaskRequest;
import com.taskmanagement.dto.TaskResponse;
import com.taskmanagement.service.TaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {
    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    @GetMapping(params = {"!userId", "!cursor", "!size"})
    public List<TaskResponse> findAll() {
        return service.findAll();
    }

    @GetMapping(params = "userId")
    public CursorPage<TaskSummaryResponse> listByUser(
            @RequestParam @Positive Long userId,
            @RequestParam(required = false) @Positive Long cursor,
            @RequestParam(required = false) @Positive Integer size) {
        return service.listByUser(userId, cursor, size);
    }

    @GetMapping("/{id}")
    public TaskResponse findById(@PathVariable @Positive Long id) {
        return service.findById(id);
    }

    @PostMapping
    public ResponseEntity<TaskResponse> create(@Valid @RequestBody TaskRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false)
            @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String idempotencyKey) {
        TaskResponse response = idempotencyKey == null ? service.create(request)
                : service.create(request, idempotencyKey);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/{id}")
    public TaskResponse update(@PathVariable @Positive Long id, @Valid @RequestBody TaskRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @Positive Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
