package com.calisthenics.backend.adminai;

import com.calisthenics.backend.admin.AdminExerciseService;
import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.AdminChatResponse;
import com.calisthenics.backend.adminai.dto.ExerciseRecommendationPayload;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftBatchResultResponse;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftCandidateDto;
import com.calisthenics.backend.adminai.dto.data.GeminiAdminToolPlan;
import com.calisthenics.backend.adminai.repository.AdminAiConversationRepository;
import com.calisthenics.backend.adminai.repository.AdminAiRecommendationRepository;
import com.calisthenics.backend.adminai.service.ExerciseDraftGenerationOrchestrator;
import com.calisthenics.backend.adminai.service.ExerciseDraftValidationService;
import com.calisthenics.backend.adminai.service.ExerciseRecommendationService;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiExerciseBatchOutcome;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExerciseGenerationExactCountTest {

    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "bulk_exercise_generation_items",
            "bulk_exercise_generation_jobs",
            "admin_ai_chat_idempotency",
            "admin_ai_pending_actions",
            "admin_ai_audit_logs",
            "admin_ai_recommendations",
            "admin_ai_messages",
            "admin_ai_conversations",
            "admin_pro_audit_logs",
            "membership_entitlements",
            "getting_started_campaign_events",
            "getting_started_day_progress",
            "getting_started_day_completions",
            "shared_personal_records",
            "personal_records",
            "workout_logs",
            "workout_session_sets",
            "workout_session_exercises",
            "workout_sessions",
            "getting_started_enrollments",
            "getting_started_program_user_states",
            "getting_started_program_exercises",
            "getting_started_program_days",
            "getting_started_programs",
            "workout_plan_assignments",
            "workout_plan_exercises",
            "workout_plan_days",
            "workout_plans",
            "coach_client_relationships",
            "chat_messages",
            "chat_conversations",
            "user_presence",
            "ai_messages",
            "ai_conversations",
            "ai_workout_plan_logs",
            "coach_review_events",
            "coach_reviews",
            "user_report_evidence",
            "user_report_actions",
            "user_reports",
            "notifications",
            "rep_histories",
            "analysis_sessions",
            "analyses",
            "video_analysis_keypoints",
            "video_analyses",
            "video_analysis_quota_usages",
            "profile_media_items",
            "client_body_measurements",
            "client_profiles",
            "coach_profiles",
            "exercise_media_items",
            "exercises",
            "password_reset_otps",
            "registration_otps",
            "users"
    );

    private static final List<ExerciseDraftCandidateDto> TEN_HSPU_EXERCISES = List.of(
            dto("Wall Plank Hold", "WALL_PLANK_HOLD", "ISOMETRIC", "BEGINNER"),
            dto("Incline Pike Push-Up", "INCLINE_PIKE_PUSH_UP", "PUSH", "BEGINNER"),
            dto("Floor Pike Push-Up", "FLOOR_PIKE_PUSH_UP", "PUSH", "BEGINNER"),
            dto("Elevated Pike Push-Up", "ELEVATED_PIKE_PUSH_UP", "PUSH", "INTERMEDIATE"),
            dto("Chest-to-Wall Handstand Hold", "CHEST_TO_WALL_HANDSTAND_HOLD", "SKILL", "INTERMEDIATE"),
            dto("Wall Handstand Scapular Shrugs", "WALL_HANDSTAND_SCAPULAR_SHRUGS", "SHOULDERS", "INTERMEDIATE"),
            dto("Wall Handstand Shoulder Taps", "WALL_HANDSTAND_SHOULDER_TAPS", "SKILL", "INTERMEDIATE"),
            dto("Negative Wall Handstand Push-Up", "NEGATIVE_WALL_HSPU", "PUSH", "ADVANCED"),
            dto("Wall Handstand Push-Up", "WALL_HANDSTAND_PUSH_UP", "PUSH", "ADVANCED"),
            dto("Deficit Wall Handstand Push-Up", "DEFICIT_WALL_HSPU", "PUSH", "ELITE"),
            dto("Freestanding Handstand Push-Up", "FREESTANDING_HSPU", "SKILL", "ELITE"),
            dto("Parallettes Handstand Push-Up", "PARALLETTES_HSPU", "PUSH", "ELITE"),
            dto("Ninety Degree Handstand Push-Up", "NINETY_DEGREE_HSPU", "SKILL", "ELITE")
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private AdminExerciseService realAdminExerciseService;
    @Autowired private AdminAiRecommendationRepository recommendationRepository;
    @Autowired private AdminAiConversationRepository conversationRepository;
    @Autowired private TransactionTemplate transactionTemplate;
    @Autowired private ExerciseDraftValidationService validationService;

    @MockBean private GeminiApiClient geminiApiClient;

    private User admin;
    private String adminToken;

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();
        when(geminiApiClient.isConfigured()).thenReturn(true);
        when(geminiApiClient.callAdminAgent(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.getLastCallDiagnostics()).thenReturn(null);

        admin = userRepository.save(User.builder()
                .email("exact_count_admin_" + System.currentTimeMillis() + "@test.com")
                .fullName("Exact Count Admin")
                .role(Role.ROLE_ADMIN)
                .build());
        adminToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(admin));
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private static ExerciseDraftCandidateDto dto(String name, String code, String category, String difficulty) {
        return ExerciseDraftCandidateDto.builder()
                .name(name)
                .code(code)
                .category(category)
                .difficulty(difficulty)
                .description("Progressive vertical pushing exercise: " + name)
                .instructions("1. Set hands shoulder-width. 2. Control range of motion with braced core.")
                .targetMuscles("Anterior Deltoids, Triceps, Upper Trapezius, Serratus Anterior")
                .equipment("Floor, Wall")
                .targetSkill("Handstand Push-Up")
                .build();
    }

    private static GeminiAdminToolPlan hspuPlan(Integer requestedCount, List<ExerciseDraftCandidateDto> drafts) {
        GeminiAdminToolPlan plan = new GeminiAdminToolPlan();
        plan.setIntent("EXERCISE_DRAFT_CREATION");
        plan.setTools(List.of("create_exercise_drafts"));
        plan.setExerciseCategory("PUSH");
        plan.setSkillKeyword("Handstand Push-Up");
        plan.setRequestedCount(requestedCount);
        plan.setExerciseDrafts(drafts);
        return plan;
    }

    @Test
    @DisplayName("Scenario 1: Gemini returns exact 10 valid Handstand Push Up progressions across PUSH/SKILL/ISOMETRIC/SHOULDERS")
    void exactTenExercisesReturnedOnFirstAttempt_savesAllTenAtomically() throws Exception {
        when(geminiApiClient.planAdminTools(anyString(), anyList()))
                .thenReturn(Optional.of(hspuPlan(10, TEN_HSPU_EXERCISES.subList(0, 10))));

        MvcResult res = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.exerciseDraftResult.requestedCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.createdCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.validCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.retryCount").value(0))
                .andExpect(jsonPath("$.createdExercises.length()").value(10))
                .andReturn();

        AdminChatResponse chatResp = objectMapper.readValue(res.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(chatResp.getExerciseDraftResult().getMessage())
                .isEqualTo("Successfully generated and saved 10/10 exercises.");
        assertThat(chatResp.getAssistantMessage().getContent())
                .contains("Successfully generated and saved 10/10 exercises.");
        assertThat(exerciseRepository.findAll()).hasSize(10);
        assertThat(exerciseRepository.findAll()).allSatisfy(ex -> {
            assertThat(ex.getStatus()).isEqualTo(ExerciseStatus.DRAFT);
            assertThat(ex.getOrigin()).isEqualTo(ExerciseOrigin.AI_RECOMMENDED);
            assertThat(ex.isActive()).isFalse();
        });
    }

    @Test
    @DisplayName("Scenario 2: Gemini initially returns only 3 exercises (and even sets requested_count=3) -> deficit refill fetches 7 more -> saves exact 10")
    void initialThreeThenSevenRefill_savesExactTenAndExcludesCollectedNames() throws Exception {
        // Reproduces exact bug: Gemini returns only 3 items even though admin asked for 10
        when(geminiApiClient.planAdminTools(anyString(), anyList()))
                .thenReturn(Optional.of(hspuPlan(3, TEN_HSPU_EXERCISES.subList(0, 3))));

        when(geminiApiClient.generateExerciseDraftBatch(anyString(), anyString()))
                .thenReturn(GeminiExerciseBatchOutcome.ok(
                        TEN_HSPU_EXERCISES.subList(3, 10),
                        1850,
                        "STOP",
                        "gemini-flash-lite-latest",
                        310L,
                        620
                ));

        MvcResult res = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.exerciseDraftResult.requestedCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.createdCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.validCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.retryCount").value(1))
                .andExpect(jsonPath("$.createdExercises.length()").value(10))
                .andReturn();

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiApiClient).generateExerciseDraftBatch(anyString(), userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue())
                .contains("EXACTLY 7 additional unique")
                .contains("Wall Plank Hold")
                .contains("Incline Pike Push-Up")
                .contains("Floor Pike Push-Up");

        AdminChatResponse chatResp = objectMapper.readValue(res.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(chatResp.getCreatedExercises()).hasSize(10);
        assertThat(exerciseRepository.findAll()).hasSize(10);
    }

    @Test
    @DisplayName("Scenario 3 & 9: Catalog duplicates + intra-batch alias duplicates are filtered and replaced via refill with ZERO DB duplicates")
    void duplicateCandidatesReplacedViaRefill_neverPersistsDuplicates() throws Exception {
        // Pre-seed two catalog exercises including one whose alias/name collides with AI output
        exerciseRepository.save(Exercise.builder()
                .name("Wall Handstand Push-Up")
                .code("WALL_HANDSTAND_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.ADVANCED)
                .trainingType(ExerciseTrainingType.CALISTHENICS)
                .description("Strict vertical pressing against wall.")
                .instructions("Lower head gently to floor and press back up.")
                .targetMuscles("Deltoids, Triceps")
                .equipment("Wall")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());
        exerciseRepository.save(Exercise.builder()
                .name("Floor Pike Push-Up")
                .code("FLOOR_PIKE_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .trainingType(ExerciseTrainingType.CALISTHENICS)
                .description("Hinged overhead pressing movement.")
                .instructions("Move head forward of hands to touch floor.")
                .targetMuscles("Shoulders, Triceps")
                .equipment("Floor")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());

        // Attempt 1 gives 10 candidates containing:
        // - duplicate catalog alias "Wall HSPU"
        // - exact catalog duplicate "Floor Pike Push-Up"
        // - intra-batch duplicate "Wall Plank Hold" twice
        // Leaving only 7 unique non-duplicates!
        List<ExerciseDraftCandidateDto> firstWithDuplicates = List.of(
                TEN_HSPU_EXERCISES.get(0), // Wall Plank Hold (valid unique #1)
                dto("Wall Plank Hold", "WALL_PLANK_HOLD_ALT", "ISOMETRIC", "BEGINNER"), // intra-batch duplicate
                dto("Wall HSPU", "WALL_HSPU_ALIAS", "PUSH", "ADVANCED"), // canonical alias of DB "Wall Handstand Push-Up"
                TEN_HSPU_EXERCISES.get(2), // Floor Pike Push-Up (DB duplicate)
                TEN_HSPU_EXERCISES.get(1), // Incline Pike Push-Up (#2)
                TEN_HSPU_EXERCISES.get(3), // Elevated Pike Push-Up (#3)
                TEN_HSPU_EXERCISES.get(4), // Chest-to-Wall Handstand Hold (#4)
                TEN_HSPU_EXERCISES.get(5), // Wall Handstand Scapular Shrugs (#5)
                TEN_HSPU_EXERCISES.get(6), // Wall Handstand Shoulder Taps (#6)
                TEN_HSPU_EXERCISES.get(7)  // Negative Wall Handstand Push-Up (#7)
        );

        when(geminiApiClient.planAdminTools(anyString(), anyList()))
                .thenReturn(Optional.of(hspuPlan(10, firstWithDuplicates)));

        // Refill retry for remaining 3 supplies fresh non-duplicates
        when(geminiApiClient.generateExerciseDraftBatch(anyString(), anyString()))
                .thenReturn(GeminiExerciseBatchOutcome.ok(
                        TEN_HSPU_EXERCISES.subList(9, 12), // Deficit Wall, Freestanding, Parallettes (#8, #9, #10)
                        980,
                        "STOP",
                        "gemini-flash-lite-latest",
                        240L,
                        320
                ));

        MvcResult res = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.exerciseDraftResult.createdCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.duplicateCount").value(2))
                .andExpect(jsonPath("$.exerciseDraftResult.rejectedCount").value(1))
                .andExpect(jsonPath("$.exerciseDraftResult.retryCount").value(1))
                .andExpect(jsonPath("$.createdExercises.length()").value(10))
                .andReturn();

        AdminChatResponse body = objectMapper.readValue(res.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(exerciseRepository.findAll()).hasSize(12); // 2 pre-existing + 10 brand new unique drafts
        assertThat(body.getCreatedExercises()).extracting(e -> e.name())
                .doesNotContain("Wall Handstand Push-Up", "Floor Pike Push-Up", "Wall HSPU");

        // Repeat exact same prompt in same conversation -> idempotent protection creates 0 additional rows
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(body.getConversationId())
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.createdExercises.length()").value(0));

        assertThat(exerciseRepository.findAll()).hasSize(12);
    }

    @Test
    @DisplayName("Scenario 4 & 7: Malformed JSON / timeout / MAX_TOKENS truncation recover on retry or fail cleanly with 0 DB writes")
    void malformedJsonAndTimeout_retryRecoversOrFailsSafelyWithoutSaving() throws Exception {
        // Case A: Initial planner returned malformed JSON (Optional.empty + MALFORMED_JSON diagnostics),
        // Refill Attempt 1 timed out (API_TIMEOUT), Refill Attempt 2 recovered all 10!
        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.getLastCallDiagnostics()).thenReturn(
                new GeminiApiClient.GeminiCallDiagnostics(
                        GeminiExerciseBatchOutcome.Status.MALFORMED_JSON, 128, "STOP", "Unexpected EOF", "gemini-flash-lite-latest", 180L, 45
                )
        );
        when(geminiApiClient.generateExerciseDraftBatch(anyString(), anyString()))
                .thenReturn(GeminiExerciseBatchOutcome.failed(
                        GeminiExerciseBatchOutcome.Status.API_TIMEOUT, 0, null, "Read timed out", "gemini-flash-lite-latest", 30000L, null))
                .thenReturn(GeminiExerciseBatchOutcome.ok(
                        TEN_HSPU_EXERCISES.subList(0, 10), 2100, "STOP", "gemini-flash-lite-latest", 420L, 710));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.exerciseDraftResult.createdCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.retryCount").value(2));
        assertThat(exerciseRepository.findAll()).hasSize(10);

        cleanDb();
        admin = userRepository.save(User.builder()
                .email("timeout_admin_" + System.currentTimeMillis() + "@test.com")
                .fullName("Timeout Admin")
                .role(Role.ROLE_ADMIN)
                .build());
        String freshToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(admin));

        // Case B: Persistent MAX_TOKENS truncation -> exhausts retries safely, ZERO DB writes, clear message
        when(geminiApiClient.generateExerciseDraftBatch(anyString(), anyString()))
                .thenReturn(GeminiExerciseBatchOutcome.failed(
                        GeminiExerciseBatchOutcome.Status.MAX_TOKENS_TRUNCATED, 8190, "MAX_TOKENS", "Truncated mid-array", "gemini-flash-lite-latest", 900L, 8192));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + freshToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(false))
                .andExpect(jsonPath("$.exerciseDraftResult.createdCount").value(0))
                .andExpect(jsonPath("$.exerciseDraftResult.validCount").value(0))
                .andExpect(jsonPath("$.exerciseDraftResult.failureReasonCode").value("MAX_TOKENS_TRUNCATED"))
                .andExpect(jsonPath("$.exerciseDraftResult.errorMessage").value(
                        "Generated 0/10 valid exercises (AI response was truncated before completing the list). No exercises were saved. Please retry."));

        assertThat(exerciseRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Scenario 5: Candidates with missing required fields or unrelated category are rejected per-item without dropping valid items")
    void missingRequiredFieldsRejectedIndividually_keepsValidItemsAndRefillsDeficit() throws Exception {
        ExerciseDraftCandidateDto missingDescription = ExerciseDraftCandidateDto.builder()
                .name("Broken Missing Description HSPU").code("BROKEN_DESC")
                .category("PUSH").difficulty("INTERMEDIATE")
                .description("") // Missing required description
                .instructions("Do push-up").targetMuscles("Shoulders").equipment("Wall").build();
        ExerciseDraftCandidateDto unrelatedCategory = ExerciseDraftCandidateDto.builder()
                .name("Barbell Back Squat").code("BARBELL_BACK_SQUAT")
                .category("LEGS").difficulty("INTERMEDIATE")
                .description("Leg exercise").instructions("Squat low").targetMuscles("Quads").equipment("Barbell").build();

        List<ExerciseDraftCandidateDto> mixedBatch = List.of(
                TEN_HSPU_EXERCISES.get(0),
                missingDescription,
                TEN_HSPU_EXERCISES.get(1),
                unrelatedCategory,
                TEN_HSPU_EXERCISES.get(2),
                TEN_HSPU_EXERCISES.get(3),
                TEN_HSPU_EXERCISES.get(4),
                TEN_HSPU_EXERCISES.get(5),
                TEN_HSPU_EXERCISES.get(6),
                TEN_HSPU_EXERCISES.get(7)
        );

        when(geminiApiClient.planAdminTools(anyString(), anyList()))
                .thenReturn(Optional.of(hspuPlan(10, mixedBatch)));
        when(geminiApiClient.generateExerciseDraftBatch(anyString(), anyString()))
                .thenReturn(GeminiExerciseBatchOutcome.ok(
                        TEN_HSPU_EXERCISES.subList(8, 10), 640, "STOP", "gemini-flash-lite-latest", 190L, 210));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.exerciseDraftResult.createdCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.rejectedCount").value(2))
                .andExpect(jsonPath("$.exerciseDraftResult.retryCount").value(1))
                .andExpect(jsonPath("$.createdExercises.length()").value(10));

        assertThat(exerciseRepository.findAll()).hasSize(10);
        assertThat(exerciseRepository.findByNameIgnoreCase("Broken Missing Description HSPU")).isEmpty();
        assertThat(exerciseRepository.findByNameIgnoreCase("Barbell Back Squat")).isEmpty();
    }

    @Test
    @DisplayName("Scenario 6: Fewer than N valid exercises after exhausting max retries returns 8/10 partial failure and writes ZERO DB rows")
    void fewerExercisesAfterMaxRetries_returnsPartialFailureAndSavesNothing() throws Exception {
        when(geminiApiClient.planAdminTools(anyString(), anyList()))
                .thenReturn(Optional.of(hspuPlan(5, TEN_HSPU_EXERCISES.subList(0, 5))));
        when(geminiApiClient.generateExerciseDraftBatch(anyString(), anyString()))
                .thenReturn(GeminiExerciseBatchOutcome.ok(TEN_HSPU_EXERCISES.subList(5, 7), 400, "STOP", "gemini-flash-lite-latest", 120L, 140))
                .thenReturn(GeminiExerciseBatchOutcome.ok(TEN_HSPU_EXERCISES.subList(7, 8), 220, "STOP", "gemini-flash-lite-latest", 100L, 80))
                .thenReturn(GeminiExerciseBatchOutcome.ok(List.of(), 20, "STOP", "gemini-flash-lite-latest", 90L, 10));

        MvcResult res = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Generate 10 exercises for Handstand Push Up")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(false))
                .andExpect(jsonPath("$.exerciseDraftResult.requestedCount").value(10))
                .andExpect(jsonPath("$.exerciseDraftResult.createdCount").value(0))
                .andExpect(jsonPath("$.exerciseDraftResult.validCount").value(8))
                .andExpect(jsonPath("$.exerciseDraftResult.retryCount").value(3))
                .andExpect(jsonPath("$.exerciseDraftResult.failureReasonCode").value("PARTIAL_COUNT"))
                .andExpect(jsonPath("$.exerciseDraftResult.errorMessage").value(
                        "Generated 8/10 valid exercises. No exercises were saved. Please retry."))
                .andExpect(jsonPath("$.createdExercises.length()").value(0))
                .andReturn();

        AdminChatResponse body = objectMapper.readValue(res.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(body.getAssistantMessage().getContent())
                .contains("Generated 8/10 valid exercises. No exercises were saved. Please retry.")
                .contains("No exercises were added to the database");
        assertThat(exerciseRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("Scenario 8: Database persistence is strictly atomic — mid-batch failure on item 8 rolls back ALL writes cleanly")
    void atomicPersistenceFailureMidBatch_rollsBackAllPreviousInserts() {
        AtomicInteger calls = new AtomicInteger();
        AdminExerciseService failingOn8th = Mockito.mock(AdminExerciseService.class);
        when(failingOn8th.createExercise(any())).thenAnswer(inv -> {
            if (calls.incrementAndGet() == 8) {
                throw new IllegalStateException("Simulated constraint failure on 8th exercise");
            }
            return realAdminExerciseService.createExercise(inv.getArgument(0));
        });

        ExerciseRecommendationService isolatedService = new ExerciseRecommendationService(
                exerciseRepository,
                failingOn8th,
                recommendationRepository,
                conversationRepository,
                validationService,
                objectMapper
        );
        ReflectionTestUtils.setField(isolatedService, "transactionTemplate", transactionTemplate);

        ExerciseDraftValidationService.ExerciseDraftRequestContract contract =
                validationService.parseRequestContract("Generate 10 exercises for Handstand Push Up", null, null, null, null, 10);

        List<ExerciseRecommendationPayload> payloads = TEN_HSPU_EXERCISES.subList(0, 10).stream()
                .map(d -> ExerciseRecommendationPayload.builder()
                        .name(d.getName()).code(d.getCode())
                        .category(ExerciseCategory.valueOf(d.getCategory()))
                        .difficulty(DifficultyLevel.valueOf(d.getDifficulty()))
                        .description(d.getDescription()).instructions(d.getInstructions())
                        .targetMuscles(d.getTargetMuscles()).equipment(d.getEquipment())
                        .targetSkill(d.getTargetSkill())
                        .build())
                .toList();

        ExerciseDraftBatchResultResponse result = isolatedService.persistExactDraftBatch(
                admin,
                null,
                "Generate 10 exercises for Handstand Push Up",
                contract,
                "PUSH",
                payloads,
                0,
                0,
                0
        );

        assertThat(calls.get()).isEqualTo(8);
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getFailureReasonCode()).isEqualTo("ATOMIC_PERSISTENCE_FAILED");
        assertThat(result.getCreatedCount()).isZero();
        assertThat(result.getCreatedExercises()).isEmpty();
        assertThat(result.getErrorMessage()).contains("No exercises were saved");
        // Verify items 1..7 were completely rolled back — ZERO partial rows in database!
        assertThat(exerciseRepository.findAll()).isEmpty();
        assertThat(recommendationRepository.findAll()).isEmpty();
    }
}
