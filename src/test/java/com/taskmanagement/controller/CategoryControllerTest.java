package com.taskmanagement.controller;

import com.taskmanagement.dto.CategoryResponse;
import com.taskmanagement.repository.UserRepository;
import com.taskmanagement.security.SecurityTestConfig;
import com.taskmanagement.service.CategoryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
@Import(SecurityTestConfig.class)
@TestPropertySource(properties = SecurityTestConfig.SECRET_PROPERTY)
class CategoryControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean UserRepository users;
    @MockitoBean CategoryService categories;

    @Test
    @WithMockUser(username = "1", roles = "ADMIN")
    void adminCanListCategories() throws Exception {
        when(categories.getAll()).thenReturn(List.of(new CategoryResponse(3L, "Work", null)));

        mvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(3))
                .andExpect(jsonPath("$[0].name").value("Work"));
    }

    @Test
    @WithMockUser(username = "1", roles = "ADMIN")
    void invalidRequestIsRejectedBeforeService() throws Exception {
        mvc.perform(post("/api/categories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        verifyNoInteractions(categories);
    }

    @Test
    @WithMockUser(username = "1", roles = "USER")
    void ordinaryUserCannotAccessCategories() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isForbidden());
        verifyNoInteractions(categories);
    }

    @Test
    void anonymousUserMustAuthenticate() throws Exception {
        mvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
        verifyNoInteractions(categories);
    }
}
