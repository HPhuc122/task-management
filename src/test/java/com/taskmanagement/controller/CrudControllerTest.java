package com.taskmanagement.controller;

import com.taskmanagement.exception.ResourceNotFoundException;
import com.taskmanagement.service.ProjectService;
import com.taskmanagement.service.TaskService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({TaskController.class, ProjectController.class})
class CrudControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean TaskService tasks;
    @MockitoBean ProjectService projects;

    @ParameterizedTest
    @ValueSource(strings = {
            "{}", "{\"title\":\" \",\"userId\":1}",
            "{\"title\":\"Task\",\"userId\":0}",
            "{\"title\":\"Task\",\"userId\":1,\"categoryId\":-1}",
            "{\"title\":\"Task\",\"userId\":1,\"projectId\":0}",
            "{\"title\":\"Task\",\"userId\":1,\"status\":\"INVALID\"}",
            "{\"title\":\"Task\",\"userId\":1,\"priority\":\"INVALID\"}",
            "{\"title\":\"Task\",\"userId\":1,\"dueDate\":\"yesterday\"}", "{"
    })
    void rejectsInvalidTaskWithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/tasks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(tasks);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\" \",\"userId\":1}",
            "{\"name\":\"Project\",\"userId\":-1}"})
    void rejectsInvalidProjectWithoutCallingService(String body) throws Exception {
        mvc.perform(post("/api/projects").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors").isMap());
        verifyNoInteractions(projects);
    }

    @Test
    void validatesUpdatesAndMaximumLengths() throws Exception {
        mvc.perform(put("/api/tasks/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + "a".repeat(256) + "\",\"userId\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.title").exists());
        mvc.perform(put("/api/projects/1").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "a".repeat(256) + "\",\"userId\":1}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(tasks, projects);
    }

    @Test
    void returnsNotFoundAsProblemDetail() throws Exception {
        when(tasks.findById(99L)).thenThrow(new ResourceNotFoundException("Task", 99L));
        mvc.perform(get("/api/tasks/99"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Task with id 99 was not found"));
    }

    @Test
    void returnsConflictWithoutLeakingDatabaseDetails() throws Exception {
        doThrow(new DataIntegrityViolationException("private SQL details")).when(projects).delete(1L);
        mvc.perform(delete("/api/projects/1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "The operation conflicts with existing data or a database constraint"));
    }

    @Test
    void rejectsNonNumericId() throws Exception {
        mvc.perform(get("/api/tasks/abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/projects/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(tasks, projects);
    }
}
