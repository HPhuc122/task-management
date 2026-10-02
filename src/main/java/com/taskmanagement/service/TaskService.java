package com.taskmanagement.service;

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
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TaskService {
    private final TaskRepository tasks;
    private final UserRepository users;
    private final CategoryRepository categories;
    private final ProjectRepository projects;

    public TaskService(TaskRepository tasks, UserRepository users,
                       CategoryRepository categories, ProjectRepository projects) {
        this.tasks = tasks;
        this.users = users;
        this.categories = categories;
        this.projects = projects;
    }

    public List<TaskResponse> findAll() {
        return tasks.findAll(Sort.by("id")).stream().map(TaskResponse::from).toList();
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
        return tasks.findById(id).orElseThrow(() -> new ResourceNotFoundException("Task", id));
    }

    private void apply(Task task, TaskRequest request) {
        task.setUser(users.findById(request.userId())
                .orElseThrow(() -> new ResourceNotFoundException("User", request.userId())));
        task.setCategory(request.categoryId() == null ? null : categories.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category", request.categoryId())));
        task.setProject(request.projectId() == null ? null : projects.findById(request.projectId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", request.projectId())));
        task.setTitle(request.title().strip());
        task.setDescription(request.description());
        task.setStatus(request.status() == null ? TaskStatus.TODO : request.status());
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setDueDate(request.dueDate());
    }
}
