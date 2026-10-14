package com.taskmanagement.service;

import com.taskmanagement.dto.TaskRequest;
import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.TaskPriority;
import com.taskmanagement.entity.TaskStatus;
import com.taskmanagement.entity.User;
import com.taskmanagement.notification.NotificationOutbox;
import com.taskmanagement.repository.CategoryRepository;
import com.taskmanagement.repository.ProjectRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {
    @Mock TaskRepository tasks;
    @Mock UserRepository users;
    @Mock CategoryRepository categories;
    @Mock ProjectRepository projects;
    @Mock CurrentUser currentUser;
    @Mock NotificationOutbox outbox;
    @Mock TaskIdempotencyService idempotency;
    @Mock TaskAttachmentService attachments;
    @InjectMocks TaskService service;

    @Test
    void firstPageUsesOneExtraRowAndReturnsNextCursor() {
        when(tasks.findFirstPageByUserId(eq(7L), any(Pageable.class)))
                .thenReturn(List.of(task(3L), task(2L), task(1L)));

        var page = service.listByUser(7L, null, 2);

        assertEquals(List.of(3L, 2L), page.content().stream().map(item -> item.id()).toList());
        assertEquals(2L, page.nextCursor());
        assertTrue(page.hasNext());
        verify(tasks).findFirstPageByUserId(eq(7L), argThat(limit -> limit.getPageSize() == 3));
    }

    @Test
    void nextPageCapsRequestedSizeAndHasNoCursorAtEnd() {
        when(tasks.findNextPageByUserId(eq(7L), eq(500L), any(Pageable.class)))
                .thenReturn(List.of(task(4L)));

        var page = service.listByUser(7L, 500L, 500);

        assertEquals(List.of(4L), page.content().stream().map(item -> item.id()).toList());
        assertNull(page.nextCursor());
        assertFalse(page.hasNext());
        verify(tasks).findNextPageByUserId(eq(7L), eq(500L),
                argThat(limit -> limit.getPageSize() == 101));
    }

    @Test
    void unauthorizedOwnerDoesNotReachRepository() {
        doThrow(new AccessDeniedException("forbidden")).when(currentUser).requireOwner(8L);

        assertThrows(AccessDeniedException.class, () -> service.listByUser(8L, null, 20));
        verifyNoInteractions(tasks);
    }

    @Test
    void createNormalizesDefaultsAndWritesOutboxAfterSaving() {
        User owner = new User();
        ReflectionTestUtils.setField(owner, "id", 7L);
        when(users.findById(7L)).thenReturn(Optional.of(owner));
        when(tasks.saveAndFlush(any(Task.class))).thenAnswer(call -> call.getArgument(0));
        TaskRequest request = new TaskRequest("  Work  ", null, null, null, null, 7L, null, null);

        service.create(request);

        verify(tasks).saveAndFlush(argThat(saved -> saved.getTitle().equals("Work")
                && saved.getStatus() == TaskStatus.TODO
                && saved.getPriority() == TaskPriority.MEDIUM
                && saved.getUser() == owner));
        verify(outbox).taskCreated(any(Task.class));
    }

    private Task task(Long id) {
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", id);
        task.setTitle("Task " + id);
        return task;
    }
}
