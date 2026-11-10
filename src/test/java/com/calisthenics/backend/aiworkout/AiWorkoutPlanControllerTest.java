package com.calisthenics.backend.aiworkout;

import com.calisthenics.backend.aiworkout.controller.AiWorkoutPlanController;
import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.exception.AiWorkoutPlanValidationException;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.security.JwtAuthenticationFilter;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AiWorkoutPlanController.class,
        excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
)
@AutoConfigureMockMvc(addFilters = false)
class AiWorkoutPlanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiWorkoutPlanService aiWorkoutPlanService;

    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(10L)
                .email("athlete@test.com")
                .fullName("Test Athlete")
                .role(Role.ROLE_USER)
                .passwordHash("password")
                .build();
        testUser = UserPrincipal.create(user);

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(testUser, null, testUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("POST /api/v1/ai/workout-plans/generate returns 200 with preview")
    void testGenerateEndpoint() throws Exception {
        GenerateAiWorkoutPlanRequest request = GenerateAiWorkoutPlanRequest.builder()
                .prompt("Create a 5-day calisthenics plan")
                .fitnessLevel("INTERMEDIATE")
                .build();

        AiWorkoutPlanPreviewResponse preview = AiWorkoutPlanPreviewResponse.builder()
                .planName("5-Day Calisthenics Program")
                .description("Structured 5-day calisthenics plan")
                .durationWeeks(4)
                .splitType(WorkoutSplitType.CUSTOM)
                .sessions(List.of(
                        AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                                .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                                .title("Push Day")
                                .restDay(false)
                                .exercises(Collections.emptyList())
                                .build()
                ))
                .metadata(AiWorkoutPlanPreviewResponse.AiWorkoutMetadata.builder()
                        .modelUsed("default-planner-model")
                        .inputTokens(120)
                        .outputTokens(350)
                        .latencyMs(450L)
                        .build())
                .build();

        when(aiWorkoutPlanService.generatePlan(eq(10L), any(GenerateAiWorkoutPlanRequest.class), nullable(String.class)))
                .thenReturn(preview);

        mockMvc.perform(post("/api/v1/ai/workout-plans/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planName").value("5-Day Calisthenics Program"))
                .andExpect(jsonPath("$.durationWeeks").value(4))
                .andExpect(jsonPath("$.metadata.modelUsed").value("default-planner-model"));
    }

    @Test
    @DisplayName("POST /api/v1/ai/workout-plans/generate returns 422 when validator rejects invalid AI plan")
    void testGenerateEndpointReturns422OnValidationError() throws Exception {
        GenerateAiWorkoutPlanRequest request = GenerateAiWorkoutPlanRequest.builder()
                .prompt("Create a 14-day workout plan")
                .fitnessLevel("INTERMEDIATE")
                .build();

        when(aiWorkoutPlanService.generatePlan(eq(10L), any(GenerateAiWorkoutPlanRequest.class), nullable(String.class)))
                .thenThrow(new AiWorkoutPlanValidationException(
                        "Training days per week cannot exceed 7 (requested 14).",
                        List.of("Training days per week cannot exceed 7 (requested 14).")
                ));

        mockMvc.perform(post("/api/v1/ai/workout-plans/generate")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.details[0]").value("Training days per week cannot exceed 7 (requested 14)."));
    }

    @Test
    @DisplayName("POST /api/v1/ai/workout-plans/commit returns 201 with persisted plan")
    void testCommitEndpoint() throws Exception {
        CommitAiWorkoutPlanRequest request = CommitAiWorkoutPlanRequest.builder()
                .plan(AiWorkoutPlanPreviewResponse.builder()
                        .planName("Committed Plan")
                        .durationWeeks(4)
                        .sessions(List.of(
                                AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                                        .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                                        .title("Push")
                                        .restDay(false)
                                        .exercises(Collections.emptyList())
                                        .build()
                        ))
                        .build())
                .idempotencyKey("idemp-key-555")
                .startDate(LocalDate.of(2026, 11, 16))
                .build();

        WorkoutPlanResponse response = WorkoutPlanResponse.builder()
                .id(105L)
                .name("Committed Plan")
                .active(true)
                .build();

        when(aiWorkoutPlanService.commitPlan(eq(10L), any(CommitAiWorkoutPlanRequest.class), nullable(String.class)))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/ai/workout-plans/commit")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(105L))
                .andExpect(jsonPath("$.name").value("Committed Plan"))
                .andExpect(jsonPath("$.isActive").value(true));
    }
}
