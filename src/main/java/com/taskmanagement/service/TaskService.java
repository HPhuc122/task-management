package com.taskmanagement.service;

import com.taskmanagement.dto.CursorPage;
import com.taskmanagement.dto.TaskSummaryResponse;
import com.taskmanagement.dto.TaskRequest;
import com.taskmanagement.dto.TaskResponse;
import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.TaskPriority;
import com.taskmanagement.entity.TaskStatus;
import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.repository.CategoryRepository;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.security.CurrentUser;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TaskRepository tasks;
    private final CurrentUser currentUser;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProjectRepository projects;

    public TaskService(TaskRepository tasks, UserRepository users,
                       CategoryRepository categories, ProjectRepository projects, CurrentUser currentUser) {
        this.tasks = tasks;
        this.currentUser = currentUser;
        this.users = users;
        this.categories = categories;
        this.projects = projects;
    }

    public CursorPage<TaskSummaryResponse> listByUser(Long userId, Long cursor, Integer requestedSize) {
        currentUser.requireOwner(userId);
        int pageSize = requestedSize == null ? DEFAULT_PAGE_SIZE : Math.min(requestedSize, MAX_PAGE_SIZE);
        PageRequest limit = PageRequest.of(0, pageSize + 1);
        List<Task> rows = cursor == null
                ? tasks.findFirstPageByUserId(userId, limit)
                : tasks.findNextPageByUserId(userId, cursor, limit);
        boolean hasNext = rows.size() > pageSize;
        List<Task> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        List<TaskSummaryResponse> content = pageRows.stream().map(TaskSummaryResponse::fromEntity).toList();
        Long nextCursor = hasNext ? pageRows.get(pageRows.size() - 1).getId() : null;
        return new CursorPage<>(content, nextCursor, hasNext);
    }

    public List<TaskResponse> findAll() {
        return (currentUser.isAdmin() ? tasks.findAll(Sort.by("id"))
                : tasks.findAllByUserId(currentUser.id(), Sort.by("id")))
                .stream().map(TaskResponse::from).toList();
    }

    public TaskResponse findById(Long id) {
        return TaskResponse.from(requireTask(id));
    }

    @Transactional
    public TaskResponse create(TaskRequest request) {
        Task task = new Task();
        apply(task, request);
        return TaskResponse.from(tasks.saveAndFlush(task));
    }

    @Transactional
    public TaskResponse update(Long id, TaskRequest request) {
        Task task = requireTask(id);
        apply(task, request);
        return TaskResponse.from(tasks.saveAndFlush(task));
    }

    @Transactional
    public void delete(Long id) {
        tasks.delete(requireTask(id));
        tasks.flush();
    }

    private Task requireTask(Long id) {
        Task resource = tasks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id));
        currentUser.requireOwner(resource.getUser().getId());
        return resource;
    }

    private void apply(Task task, TaskRequest request) {
        currentUser.requireOwner(request.userId());
        task.setUser(users.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId())));
        task.setCategory(request.categoryId() == null ? null : categories.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId())));
        task.setProject(request.projectId() == null ? null : projects.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId())));
        if (task.getProject() != null) {
            currentUser.requireOwner(task.getProject().getUser().getId());
        }
        task.setTitle(request.title().strip());
        task.setDescription(request.description());
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
    }
}
