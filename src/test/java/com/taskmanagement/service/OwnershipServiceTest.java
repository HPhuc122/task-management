package com.taskmanagement.service;

import com.taskmanagement.dto.ProjectRequest;
import com.taskmanagement.dto.TaskRequest;
import com.taskmanagement.entity.Project;
import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.User;
import com.taskmanagement.repository.*;
import com.taskmanagement.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class OwnershipServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final CurrentUser current = new CurrentUser();
    private final TaskService taskService = new TaskService(tasks, users, categories, projects, current);
    private final ProjectService projectService = new ProjectService(projects, users, current);

    @BeforeEach
    void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsOnlyCurrentOwnersResources() {
        when(tasks.findAllByUserId(1L, Sort.by("id"))).thenReturn(List.of());
        when(projects.findAllByUserId(1L, Sort.by("id"))).thenReturn(List.of());
        taskService.findAll();
        projectService.findAll();
        verify(tasks).findAllByUserId(1L, Sort.by("id"));
        verify(projects).findAllByUserId(1L, Sort.by("id"));
        verify(tasks, never()).findAll(any(Sort.class));
        verify(projects, never()).findAll(any(Sort.class));
    }

    @Test
    void cannotReadUpdateOrDeleteOtherOwnersResources() {
        Task task = new Task();
        task.setUser(owner(2L));
        Project project = new Project();
        project.setUser(owner(2L));
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        when(projects.findById(20L)).thenReturn(Optional.of(project));
        assertThrows(AccessDeniedException.class, () -> taskService.findById(10L));
        assertThrows(AccessDeniedException.class, () -> taskService.update(10L, taskRequest(1L, null)));
        assertThrows(AccessDeniedException.class, () -> taskService.delete(10L));
        assertThrows(AccessDeniedException.class, () -> projectService.findById(20L));
        assertThrows(AccessDeniedException.class, () -> projectService.update(20L, new ProjectRequest("name", null, 1L)));
        assertThrows(AccessDeniedException.class, () -> projectService.delete(20L));
        verify(tasks, never()).saveAndFlush(any());
        verify(tasks, never()).delete(any());
        verify(projects, never()).saveAndFlush(any());
        verify(projects, never()).delete(any());
    }

    @Test
    void cannotForgeOwnerOrLinkOtherOwnersProject() {
        assertThrows(AccessDeniedException.class, () -> taskService.create(taskRequest(2L, null)));
        assertThrows(AccessDeniedException.class, () -> projectService.create(new ProjectRequest("name", null, 2L)));
        Project project = new Project();
        project.setUser(owner(2L));
        when(projects.findById(20L)).thenReturn(Optional.of(project));
        when(users.findById(1L)).thenReturn(Optional.of(owner(1L)));
        assertThrows(AccessDeniedException.class, () -> taskService.create(taskRequest(1L, 20L)));
        verify(tasks, never()).saveAndFlush(any());
        verify(projects, never()).saveAndFlush(any());
    }

    @Test
    void adminCanListAndReadOtherOwnersResources() {
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
        when(tasks.findAll(Sort.by("id"))).thenReturn(List.of());
        when(projects.findAll(Sort.by("id"))).thenReturn(List.of());
        Task task = new Task();
        task.setUser(owner(2L));
        when(tasks.findById(10L)).thenReturn(Optional.of(task));
        assertEquals(2L, taskService.findById(10L).userId());
        taskService.findAll();
        projectService.findAll();
        verify(tasks).findAll(Sort.by("id"));
        verify(projects).findAll(Sort.by("id"));
    }

    @Test
    void serviceFailsClosedWithoutAuthentication() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, taskService::findAll);
        assertThrows(AuthenticationCredentialsNotFoundException.class, projectService::findAll);
        assertThrows(AuthenticationCredentialsNotFoundException.class, () -> taskService.create(taskRequest(1L, null)));
    }

    private User owner(Long id) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private TaskRequest taskRequest(Long userId, Long projectId) {
        return new TaskRequest("Task", null, null, null, null, userId, null, projectId);
    }
}
