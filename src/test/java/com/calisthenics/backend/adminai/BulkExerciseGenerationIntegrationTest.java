package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationJobResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationRequest;
import com.calisthenics.backend.adminai.repository.BulkExerciseGenerationItemRepository;
import com.calisthenics.backend.adminai.repository.BulkExerciseGenerationJobRepository;
import com.calisthenics.backend.adminai.service.BulkExerciseGenerationService;
import com.calisthenics.backend.adminai.type.BulkExerciseItemOutcome;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
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
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BulkExerciseGenerationIntegrationTest {

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
            "getting_started_campaign_events",
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private BulkExerciseGenerationJobRepository jobRepository;
    @Autowired private BulkExerciseGenerationItemRepository itemRepository;
    @Autowired private BulkExerciseGenerationService bulkGenerationService;

    @MockBean private GeminiApiClient geminiApiClient;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;
    private Exercise preExistingPushUp;
    private Exercise preExistingBarbellSquat;

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();
        when(geminiApiClient.isConfigured()).thenReturn(false);

        long ts = System.currentTimeMillis();
        adminUser = userRepository.save(User.builder()
                .email("admin_bulk_" + ts + "@test.com")
                .fullName("Bulk Admin")
                .role(Role.ROLE_ADMIN)
                .build());
        regularUser = userRepository.save(User.builder()
                .email("user_bulk_" + ts + "@test.com")
                .fullName("Normal Athlete")
                .role(Role.ROLE_USER)
                .build());

        adminToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(adminUser));
        userToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(regularUser));

        preExistingPushUp = exerciseRepository.save(Exercise.builder()
                .name("Push-Up")
                .code("PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .trainingType(ExerciseTrainingType.CALISTHENICS)
                .difficulty(DifficultyLevel.BEGINNER)
                .targetMuscles("Chest, Triceps")
                .instructions("Perform standard push-up.")
                .active(true)
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .build());

        preExistingBarbellSquat = exerciseRepository.save(Exercise.builder()
                .name("Barbell Squat")
                .code("BARBELL_SQUAT")
                .category(ExerciseCategory.LEGS)
                .trainingType(ExerciseTrainingType.GYM)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .targetMuscles("Quadriceps, Glutes")
                .instructions("Barbell back squat.")
                .active(true)
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .build());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    @Test
    @DisplayName("Bulk generate 150 new calisthenics exercises -> all persisted as DRAFT+AI_RECOMMENDED, untruncated 1..150 summary, hidden from public users")
    void generate150CalisthenicsExercises_completeUntruncatedDrafts() throws Exception {
        AdminChatRequest req = AdminChatRequest.builder()
                .message("Generate 150 new calisthenics exercises.")
                .idempotencyKey("bulk-cal-150")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("BULK_EXERCISE_GENERATION"))
                .andExpect(jsonPath("$.bulkGenerationJob.status").value("COMPLETED"))
                .andExpect(jsonPath("$.bulkGenerationJob.savedCount").value(150))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String summaryContent = root.path("assistantMessage").path("content").asText();
        assertThat(summaryContent).contains("150. **");
        assertThat(summaryContent).contains("1. **");

        List<Exercise> allExercises = exerciseRepository.findAll();
        // 2 pre-existing + 150 generated = 152
        assertThat(allExercises).hasSize(152);
        List<Exercise> aiDrafts = allExercises.stream()
                .filter(e -> e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED)
                .toList();
        assertThat(aiDrafts).hasSize(150);
        assertThat(aiDrafts).allSatisfy(ex -> {
            assertThat(ex.getStatus()).isEqualTo(ExerciseStatus.DRAFT);
            assertThat(ex.isActive()).isFalse();
            assertThat(ex.getTrainingType()).isEqualTo(ExerciseTrainingType.CALISTHENICS);
            assertThat(ex.getSecondaryMuscles()).isNotBlank();
            assertThat(ex.getRecommendedSets()).isPositive();
            assertThat(ex.getRecommendedReps()).isNotBlank();
        });

        // Pre-existing exercises remain intact and published
        assertThat(exerciseRepository.findById(preExistingPushUp.getId()).orElseThrow().isActive()).isTrue();
        assertThat(exerciseRepository.findById(preExistingBarbellSquat.getId()).orElseThrow().isActive()).isTrue();

        // Public athlete catalog MUST NOT return any DRAFT AI exercises
        MvcResult publicCatalog = mockMvc.perform(get("/api/v1/exercises")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();
        String publicJson = publicCatalog.getResponse().getContentAsString();
        assertThat(publicJson).contains("Push-Up");
        assertThat(publicJson).doesNotContain("AI_RECOMMENDED");
    }

    @Test
    @DisplayName("Bulk generate 200 gym exercises -> skips duplicate Barbell Back Squat alias & persists 200 unique GYM drafts")
    void generate200GymExercises_no100CapAndSkipsAliases() throws Exception {
        AdminChatRequest req = AdminChatRequest.builder()
                .message("Generate 200 gym exercises.")
                .idempotencyKey("bulk-gym-200")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulkGenerationJob.status").value("COMPLETED"))
                .andExpect(jsonPath("$.bulkGenerationJob.savedCount").value(200))
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        Long jobId = root.path("bulkGenerationJob").path("id").asLong();
        String summaryContent = root.path("assistantMessage").path("content").asText();
        assertThat(summaryContent).contains("200. **");

        // Barbell Back Squat must have been detected as alias duplicate of pre-existing Barbell Squat
        var skippedItems = itemRepository.findByJobIdAndOutcomeOrderBySequenceNumberAscIdAsc(
                jobId, BulkExerciseItemOutcome.DUPLICATE_SKIPPED);
        assertThat(skippedItems).anySatisfy(item ->
                assertThat(item.getExerciseName()).isEqualTo("Barbell Back Squat"));

        // Verify all 200 new exercises are unique GYM drafts
        List<Exercise> aiGymDrafts = exerciseRepository.findAll().stream()
                .filter(e -> e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED)
                .toList();
        assertThat(aiGymDrafts).hasSize(200);
        assertThat(aiGymDrafts).allMatch(e -> e.getTrainingType() == ExerciseTrainingType.GYM);
        Set<String> uniqueLowerNames = aiGymDrafts.stream()
                .map(e -> e.getName().toLowerCase())
                .collect(Collectors.toSet());
        assertThat(uniqueLowerNames).hasSize(200);
    }

    @Test
    @DisplayName("Bulk generate 100 leg exercises for BOTH calisthenics and gym -> spans both disciplines with category=LEGS")
    void generate100LegExercisesForBothDisciplines() throws Exception {
        AdminChatRequest req = AdminChatRequest.builder()
                .message("Generate 100 leg exercises for both calisthenics and gym.")
                .idempotencyKey("bulk-legs-both-100")
                .build();

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulkGenerationJob.status").value("COMPLETED"))
                .andExpect(jsonPath("$.bulkGenerationJob.trainingTypeFilter").value("BOTH"))
                .andExpect(jsonPath("$.bulkGenerationJob.savedCount").value(100));

        List<Exercise> savedLegs = exerciseRepository.findAll().stream()
                .filter(e -> e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED)
                .toList();
        assertThat(savedLegs).hasSize(100);
        assertThat(savedLegs).allMatch(e -> e.getCategory() == ExerciseCategory.LEGS);
        assertThat(savedLegs).anyMatch(e -> e.getTrainingType() == ExerciseTrainingType.CALISTHENICS);
        assertThat(savedLegs).anyMatch(e -> e.getTrainingType() == ExerciseTrainingType.GYM);
    }

    @Test
    @DisplayName("Simulated interruption & safe resumption -> preserves first 40 committed exercises and finishes to 100 with pagination")
    void simulatedInterruptionAndSafeResume_andSecurityCheck() throws Exception {
        // Non-admin receives 403 Forbidden
        BulkExerciseGenerationRequest deniedReq = BulkExerciseGenerationRequest.builder()
                .quantity(50)
                .trainingType(ExerciseTrainingType.CALISTHENICS)
                .build();
        mockMvc.perform(post("/api/v1/admin/exercises/bulk-generate")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deniedReq)))
                .andExpect(status().isForbidden());

        // Simulate interruption after 2 batches of 20 (40 saved)
        BulkExerciseGenerationRequest partialReq = BulkExerciseGenerationRequest.builder()
                .prompt("Generate 100 mixed exercises")
                .quantity(100)
                .trainingType(ExerciseTrainingType.BOTH)
                .batchSize(20)
                .build();

        BulkExerciseGenerationJobResponse partial =
                bulkGenerationService.executeWithBatchCapForRecoveryTest(adminUser, partialReq, 2);
        assertThat(partial.getStatus()).isEqualTo("PARTIAL");
        assertThat(partial.getSavedCount()).isBetween(38, 40);
        assertThat(partial.getGeneratedCount()).isEqualTo(40);
        assertThat(partial.isCanResume()).isTrue();
        assertThat(exerciseRepository.findAll().stream()
                .filter(e -> e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED).count()).isEqualTo(partial.getSavedCount());

        // Resume via Admin REST endpoint
        mockMvc.perform(post("/api/v1/admin/exercises/bulk-generate/" + partial.getId() + "/resume")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.savedCount").value(100))
                .andExpect(jsonPath("$.canResume").value(false));

        assertThat(exerciseRepository.findAll().stream()
                .filter(e -> e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED).count()).isEqualTo(100);

        // Verify paginated items endpoint works cleanly
        mockMvc.perform(get("/api/v1/admin/exercises/bulk-generate/" + partial.getId() + "/items")
                        .param("outcome", "SAVED")
                        .param("page", "0")
                        .param("size", "25")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(25))
                .andExpect(jsonPath("$.totalElements").value(100))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.hasNext").value(true));
    }
}
