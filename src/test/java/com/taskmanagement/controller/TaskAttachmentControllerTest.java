package com.taskmanagement.controller;

import com.taskmanagement.dto.TaskAttachmentResponse;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.security.SecurityTestConfig;
import com.taskmanagement.service.TaskAttachmentService;
import com.taskmanagement.service.TaskAttachmentService.AttachmentContent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskAttachmentController.class)
@Import(SecurityTestConfig.class)
@TestPropertySource(properties = SecurityTestConfig.SECRET_PROPERTY)
@WithMockUser(username = "1", roles = "USER")
class TaskAttachmentControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean TaskAttachmentService attachments;

    @Test
    void uploadsMultipartAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.txt", MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());
        var response = new TaskAttachmentResponse(7L, 3L, "notes.txt", "text/plain", 5,
                "/api/tasks/3/attachments/7/content", Instant.parse("2026-01-01T00:00:00Z"));
        when(attachments.upload(eq(3L), any())).thenReturn(response);

        mvc.perform(multipart("/api/tasks/3/attachments").file(file))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost/api/tasks/3/attachments/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.downloadUrl").value("/api/tasks/3/attachments/7/content"));
    }

    @Test
    void listsDownloadsAndDeletesAttachment() throws Exception {
        when(attachments.list(3L)).thenReturn(List.of());
        when(attachments.download(3L, 7L)).thenReturn(
                new AttachmentContent("résumé.pdf", "application/pdf", 3, new byte[]{1, 2, 3}));

        mvc.perform(get("/api/tasks/3/attachments"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get("/api/tasks/3/attachments/7/content"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Length", "3"))
                .andExpect(content().bytes(new byte[]{1, 2, 3}));
        mvc.perform(delete("/api/tasks/3/attachments/7"))
                .andExpect(status().isNoContent());
        verify(attachments).delete(3L, 7L);
    }

    @Test
    void rejectsInvalidIdsAndMissingMultipartPart() throws Exception {
        mvc.perform(get("/api/tasks/0/attachments")).andExpect(status().isBadRequest());
        mvc.perform(multipart("/api/tasks/3/attachments"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(attachments);
    }
}
