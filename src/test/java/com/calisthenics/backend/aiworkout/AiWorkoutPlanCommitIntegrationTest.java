package com.calisthenics.backend.aiworkout;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiWorkoutPlanCommitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockBean
    private GeminiApiClient geminiApiClient;

    private UserPrincipal athletePrincipal;
    private Exercise holdExercise;
    private Exercise repExercise;

    @BeforeEach
    void setUp() {
        User athlete = userRepository.save(User.builder()
                .email("ai_planner_it_" + UUID.randomUUID() + "@example.com")
                .passwordHash(passwordEncoder.encode("Password123!"))
                .fullName("Planner Test Athlete")
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build());
        athletePrincipal = UserPrincipal.create(athlete);

        holdExercise = exerciseRepository.findByCode("ADV_TUCK_PLANCHE")
                .orElseGet(() -> exerciseRepository.save(Exercise.builder()
                        .code("ADV_TUCK_PLANCHE")
                        .name("Advanced Tuck Planche")
                        .category(ExerciseCategory.SKILL)
                        .difficulty(DifficultyLevel.ADVANCED)
                        .active(true)
                        .build()));

        repExercise = exerciseRepository.findByCode("PUSH_UP")
                .orElseGet(() -> exerciseRepository.save(Exercise.builder()
                        .code("PUSH_UP")
                        .name("Push-Up")
                        .category(ExerciseCategory.PUSH)
                        .difficulty(DifficultyLevel.BEGINNER)
                        .active(true)
                        .build()));
    }

    @Test
    void generateAndCommitTwice_normalizesZeroRepsOnHoldAndHandlesActivePlanConflictCleanly() throws Exception {
        // 1. Gemini returns reps=0 + holdSeconds=12 (exact failing payload observed on prod)
        GeminiStructuredWorkoutPlan firstGeminiPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("Planche Specialization Routine")
                .description("Focus on advanced tuck planche and push strength")
                .durationWeeks(4)
                .splitType("CUSTOM")
                .sessions(List.of(
                        GeminiStructuredWorkoutPlan.GeminiSession.builder()
                                .dayOfWeek("MONDAY")
                                .title("Planche Strength")
                                .restDay(false)
                                .exercises(List.of(
                                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                                .exerciseId(holdExercise.getId())
                                                .exerciseName(holdExercise.getName())
                                                .sets(4)
                                                .reps(0)
                                                .holdSeconds(12)
                                                .restSeconds(180)
                                                .build(),
                                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                                .exerciseId(repExercise.getId())
                                                .exerciseName(repExercise.getName())
                                                .sets(3)
                                                .reps(12)
                                                .holdSeconds(0)
                                                .restSeconds(90)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(
                        firstGeminiPlan, "{}", 200, 300, 420L, "gemini-flash-lite-latest"));

        // 2. Generate first preview
        MvcResult genRes1 = mockMvc.perform(post("/api/v1/ai/workout-plans/generate")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateAiWorkoutPlanRequest.builder()
                                .prompt("Create planche routine")
                                .durationWeeks(4)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.hasConflicts").value(false))
                .andReturn();

        AiWorkoutPlanPreviewResponse preview1 = objectMapper.readValue(
                genRes1.getResponse().getContentAsString(), AiWorkoutPlanPreviewResponse.class);

        // 3. Commit first plan expecting no existing active plan (expectedActivePlanId = 0)
        MvcResult commitRes1 = mockMvc.perform(post("/api/v1/ai/workout-plans/commit")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CommitAiWorkoutPlanRequest.builder()
                                .plan(preview1)
                                .idempotencyKey("it-key-1-" + UUID.randomUUID())
                                .replaceExisting(true)
                                .expectedActivePlanId(0L)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn();

        JsonNode committed1Json = objectMapper.readTree(commitRes1.getResponse().getContentAsString());
        long plan1Id = committed1Json.get("id").asLong();

        // 4. Generate second preview -> backend detects active plan conflict
        GeminiStructuredWorkoutPlan secondGeminiPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("Front Lever & Handstand Block")
                .description("New focus")
                .durationWeeks(4)
                .splitType("CUSTOM")
                .sessions(List.of(
                        GeminiStructuredWorkoutPlan.GeminiSession.builder()
                                .dayOfWeek("TUESDAY")
                                .title("Skill Day")
                                .restDay(false)
                                .exercises(List.of(
                                        GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                                .exerciseId(holdExercise.getId())
                                                .exerciseName(holdExercise.getName())
                                                .sets(3)
                                                .reps(0)
                                                .holdSeconds(15)
                                                .restSeconds(120)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(
                        secondGeminiPlan, "{}", 210, 320, 410L, "gemini-flash-lite-latest"));

        MvcResult genRes2 = mockMvc.perform(post("/api/v1/ai/workout-plans/generate")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GenerateAiWorkoutPlanRequest.builder()
                                .prompt("Create front lever plan")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.hasConflicts").value(true))
                .andExpect(jsonPath("$.metadata.existingActivePlanName").value("Planche Specialization Routine"))
                .andReturn();

        AiWorkoutPlanPreviewResponse preview2 = objectMapper.readValue(
                genRes2.getResponse().getContentAsString(), AiWorkoutPlanPreviewResponse.class);

        // 5. Attempting commit with stale expectedActivePlanId = 0 returns genuine 409 CONFLICT
        mockMvc.perform(post("/api/v1/ai/workout-plans/commit")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CommitAiWorkoutPlanRequest.builder()
                                .plan(preview2)
                                .idempotencyKey("it-key-stale-" + UUID.randomUUID())
                                .replaceExisting(true)
                                .expectedActivePlanId(0L)
                                .build())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("Planche Specialization Routine")));

        // 6. Explicit replacement with matching expectedActivePlanId succeeds cleanly (201) and replaces active plan
        String key2 = "it-key-replace-" + UUID.randomUUID();
        MvcResult commitRes2 = mockMvc.perform(post("/api/v1/ai/workout-plans/commit")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CommitAiWorkoutPlanRequest.builder()
                                .plan(preview2)
                                .idempotencyKey(key2)
                                .replaceExisting(true)
                                .expectedActivePlanId(plan1Id)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(true))
                .andReturn();

        long plan2Id = objectMapper.readTree(commitRes2.getResponse().getContentAsString()).get("id").asLong();

        // 7. Replaying identical idempotencyKey returns plan2 without duplicating
        mockMvc.perform(post("/api/v1/ai/workout-plans/commit")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CommitAiWorkoutPlanRequest.builder()
                                .plan(preview2)
                                .idempotencyKey(key2)
                                .replaceExisting(true)
                                .expectedActivePlanId(plan1Id)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(plan2Id));

        // 8. Saving without replacing active plan (replaceExisting=false) creates inactive plan preserving plan2
        mockMvc.perform(post("/api/v1/ai/workout-plans/commit")
                        .with(user(athletePrincipal))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(CommitAiWorkoutPlanRequest.builder()
                                .plan(preview1)
                                .idempotencyKey("it-key-draft-" + UUID.randomUUID())
                                .replaceExisting(false)
                                .build())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isActive").value(false));

        // Active endpoint still points to plan2Id
        mockMvc.perform(get("/api/v1/workout-plans/active")
                        .with(user(athletePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(plan2Id));

        // Verify plan1 stored reps=null and durationSeconds=12 on hold exercise
        mockMvc.perform(get("/api/v1/workout-plans/{id}", plan1Id)
                        .with(user(athletePrincipal)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isActive").value(false))
                .andExpect(jsonPath("$.days[0].exercises[0].reps").value(nullValue()))
                .andExpect(jsonPath("$.days[0].exercises[0].durationSeconds").value(12));
    }
}
