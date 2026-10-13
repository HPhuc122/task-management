package com.taskmanagement.service;

import com.taskmanagement.config.ObjectStorageProperties;
import com.taskmanagement.entity.Task;
import com.taskmanagement.entity.TaskAttachment;
import com.taskmanagement.entity.User;
import com.taskmanagement.exception.AttachmentValidationException;
import com.taskmanagement.repository.TaskAttachmentRepository;
import com.taskmanagement.repository.TaskRepository;
import com.taskmanagement.security.CurrentUser;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TaskAttachmentServiceTest {
    private final TaskRepository tasks = mock(TaskRepository.class);
    private final TaskAttachmentRepository attachments = mock(TaskAttachmentRepository.class);
    private final ObjectStorageService storage = mock(ObjectStorageService.class);
    private final CurrentUser currentUser = new CurrentUser();
    private final TaskAttachmentService service = new TaskAttachmentService(tasks, attachments, storage,
            currentUser, new ObjectStorageProperties(null, "us-east-1", "key", "secret",
                    "task-attachments", true, false, DataSize.ofBytes(5)));

    @BeforeEach
    void authenticate() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.initSynchronization();
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                "1", null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
        TransactionSynchronizationManager.clear();
    }

    @Test
    void uploadsMetadataAndObjectWithSanitizedFileName() {
        Task task = taskOwnedBy(1L, 3L);
        when(tasks.findById(3L)).thenReturn(Optional.of(task));
        when(storage.upload(anyString(), eq("text/plain"), eq(5L), any())).thenReturn("etag");
        when(attachments.saveAndFlush(any())).thenAnswer(invocation -> {
            TaskAttachment value = invocation.getArgument(0);
            ReflectionTestUtils.setField(value, "id", 7L);
            return value;
        });

        var response = service.upload(3L,
                new MockMultipartFile("file", "../notes.txt", "text/plain", "hello".getBytes()));

        assertEquals("notes.txt", response.fileName());
        assertEquals(7L, response.id());
        verify(storage).upload(startsWith("tasks/3/"), eq("text/plain"), eq(5L), any());
    }

    @Test
    void rejectsEmptyOversizedAndOtherOwnersFilesBeforeStorage() {
        when(tasks.findById(3L)).thenReturn(Optional.of(taskOwnedBy(1L, 3L)));
        assertThrows(AttachmentValidationException.class, () -> service.upload(3L,
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])));
        assertThrows(AttachmentValidationException.class, () -> service.upload(3L,
                new MockMultipartFile("file", "large.txt", "text/plain", new byte[6])));

        when(tasks.findById(4L)).thenReturn(Optional.of(taskOwnedBy(2L, 4L)));
        assertThrows(AccessDeniedException.class, () -> service.upload(4L,
                new MockMultipartFile("file", "ok.txt", "text/plain", new byte[]{1})));
        verifyNoInteractions(storage);
    }

    @Test
    void checksTaskOwnershipBeforeListingOrDownloading() {
        when(tasks.findById(4L)).thenReturn(Optional.of(taskOwnedBy(2L, 4L)));
        assertThrows(AccessDeniedException.class, () -> service.list(4L));
        assertThrows(AccessDeniedException.class, () -> service.download(4L, 7L));
        verifyNoInteractions(attachments, storage);
    }

    @Test
    void uploadIsCleanedOnOuterRollbackButRetainedOnCommitOrUnknownOutcome() {
        when(tasks.findById(3L)).thenReturn(Optional.of(taskOwnedBy(1L, 3L)));
        when(attachments.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        for (int status : new int[]{TransactionSynchronization.STATUS_COMMITTED,
                TransactionSynchronization.STATUS_UNKNOWN, TransactionSynchronization.STATUS_ROLLED_BACK}) {
            service.upload(3L, new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}));
            var callbacks = TransactionSynchronizationManager.getSynchronizations();
            callbacks.getLast().afterCompletion(status);
        }
        verify(storage, times(1)).delete(anyString());
    }

    @Test
    void databaseFailureIsNotMaskedWhenRollbackCleanupAlsoFails() {
        when(tasks.findById(3L)).thenReturn(Optional.of(taskOwnedBy(1L, 3L)));
        var failure = new IllegalStateException("database failure");
        when(attachments.saveAndFlush(any())).thenThrow(failure);
        doThrow(new IllegalStateException("MinIO unavailable")).when(storage).delete(anyString());
        assertSame(failure, assertThrows(IllegalStateException.class, () -> service.upload(3L,
                new MockMultipartFile("file", "a.txt", "text/plain", new byte[]{1}))));
        assertDoesNotThrow(() -> TransactionSynchronizationManager.getSynchronizations().getFirst()
                .afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(storage).delete(anyString());
    }

    @Test
    void deleteWaitsForCommitAndCleanupFailureDoesNotEscape() {
        when(tasks.findById(3L)).thenReturn(Optional.of(taskOwnedBy(1L, 3L)));
        TaskAttachment row = new TaskAttachment();
        row.setObjectKey("tasks/3/object");
        when(attachments.findByIdAndTaskId(7L, 3L)).thenReturn(Optional.of(row));
        service.delete(3L, 7L);
        verify(storage, never()).delete(anyString());
        var callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
        callback.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(storage, never()).delete(anyString());
        doThrow(new IllegalStateException("MinIO unavailable")).when(storage).delete(anyString());
        assertDoesNotThrow(callback::afterCommit);
        verify(storage).delete("tasks/3/object");
    }

    private Task taskOwnedBy(Long ownerId, Long taskId) {
        User user = new User();
        ReflectionTestUtils.setField(user, "id", ownerId);
        Task task = new Task();
        ReflectionTestUtils.setField(task, "id", taskId);
        task.setUser(user);
        return task;
    }
}
