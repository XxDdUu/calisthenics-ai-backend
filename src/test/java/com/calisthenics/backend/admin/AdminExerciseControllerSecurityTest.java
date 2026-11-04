package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminExerciseControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminExerciseService adminExerciseService;

    @Test
    void unauthenticatedUserCannotAccessExercises() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exercises"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void normalUserCannotAccessAdminExercises() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exercises"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_COACH")
    void coachCannotAccessAdminExercises() throws Exception {
        mockMvc.perform(get("/api/v1/admin/exercises"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void normalUserCannotCreateExercise() throws Exception {
        mockMvc.perform(post("/api/v1/admin/exercises")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Test\",\"category\":\"PUSH\",\"difficulty\":\"BEGINNER\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_ADMIN")
    void adminCanAccessExercises() throws Exception {
        AdminPageResponse<AdminExerciseResponse> emptyPage = new AdminPageResponse<>(
                List.of(), 0, 20, 0, 0, false
        );
        when(adminExerciseService.getExercises(anyInt(), anyInt(), any(), any(), any(), any(), anyString(), anyString()))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/admin/exercises").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
