package com.taskmanagement.service;

import com.taskmanagement.dto.CursorPage;
import com.taskmanagement.dto.TaskSummaryResponse;
import com.taskmanagement.entity.Task;
import com.taskmanagement.repository.TaskRepository;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional(readOnly = true)
    public CursorPage<TaskSummaryResponse> listByUser(Long userId, Long cursor, Integer requestedSize) {
        int pageSize = normalizeSize(requestedSize);
        PageRequest limit = PageRequest.of(0, pageSize + 1);
        List<Task> rows = cursor == null
                ? taskRepository.findFirstPageByUserId(userId, limit)
                : taskRepository.findNextPageByUserId(userId, cursor, limit);

        boolean hasNext = rows.size() > pageSize;
        List<Task> pageRows = hasNext ? rows.subList(0, pageSize) : rows;

        List<TaskSummaryResponse> content = pageRows.stream()
                .map(TaskSummaryResponse::fromEntity)
                .toList();

        Long nextCursor = hasNext && !pageRows.isEmpty()
                ? pageRows.get(pageRows.size() - 1).getId()
                : null;

        return new CursorPage<>(content, nextCursor, hasNext);
    }

    private int normalizeSize(Integer requested) {
        if (requested == null || requested <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(requested, MAX_PAGE_SIZE);
    }
}
