package com.taskmanagement.controller;

import com.taskmanagement.dto.CursorPage;
import com.taskmanagement.dto.TaskSummaryResponse;
import com.taskmanagement.service.TaskService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    public CursorPage<TaskSummaryResponse> listTasks(
            @RequestParam Long userId,
            @RequestParam(required = false) Long cursor,
            @RequestParam(required = false) Integer size) {
        return taskService.listByUser(userId, cursor, size);
    }
}