package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.AdminChatResponse;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExerciseDraftAndCatalogFlowTest {

    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
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

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private UserRepository userRepository;
    @Autowired private ExerciseRepository exerciseRepository;

    @MockBean private GeminiApiClient geminiApiClient;

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;
    private Exercise publishedPushUp;

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();

        long ts = System.currentTimeMillis();
        adminUser = userRepository.save(User.builder()
                .email("admin_draft_" + ts + "@test.com")
                .fullName("Admin Tester")
                .role(Role.ROLE_ADMIN)
                .build());

        regularUser = userRepository.save(User.builder()
                .email("user_draft_" + ts + "@test.com")
                .fullName("Athlete Tester")
                .role(Role.ROLE_USER)
                .build());

        adminToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(adminUser));
        userToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(regularUser));

        publishedPushUp = exerciseRepository.save(Exercise.builder()
                .name("Push-Up")
                .code("PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .description("Standard push-up")
                .targetMuscles("Chest, Triceps")
                .instructions("Lower chest to floor and press up.")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());

        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.callAdminAgent(anyString(), anyList())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    @Test
    @DisplayName("Full lifecycle: AI creates 3 DRAFT+AI_RECOMMENDED leg exercises -> hidden from users -> Admin publishes -> visible to users, retry deduplicates")
    void fullAiDraftPersistenceAndPublishingFlow() throws Exception {
        // 1. Admin asks AI to add 3 leg exercises
        AdminChatRequest req = AdminChatRequest.builder()
                .message("Add 3 leg exercises")
                .build();

        MvcResult aiResult = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EXERCISE_DRAFT_CREATION"))
                .andExpect(jsonPath("$.createdExercises.length()").value(3))
                .andReturn();

        AdminChatResponse chatResp = objectMapper.readValue(
                aiResult.getResponse().getContentAsString(), AdminChatResponse.class);
        Long conversationId = chatResp.getConversationId();

        assertThat(chatResp.getCreatedExercises()).hasSize(3);
        assertThat(chatResp.getCreatedExercises()).allSatisfy(ex -> {
            assertThat(ex.id()).isNotNull();
            assertThat(ex.category()).isEqualTo(ExerciseCategory.LEGS);
            assertThat(ex.status()).isEqualTo(ExerciseStatus.DRAFT);
            assertThat(ex.origin()).isEqualTo(ExerciseOrigin.AI_RECOMMENDED);
            assertThat(ex.active()).isFalse();
        });

        // Verify actual DB records persisted
        List<Exercise> allInDb = exerciseRepository.findAll();
        long legDraftCount = allInDb.stream()
                .filter(e -> e.getCategory() == ExerciseCategory.LEGS
                        && e.getStatus() == ExerciseStatus.DRAFT
                        && e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED
                        && !e.isActive())
                .count();
        assertThat(legDraftCount).isEqualTo(3);

        // 2. Retrying the exact same request in the same conversation does NOT create duplicates
        AdminChatRequest retryReq = AdminChatRequest.builder()
                .conversationId(conversationId)
                .message("Add 3 leg exercises")
                .build();

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retryReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdExercises.length()").value(0));

        assertThat(exerciseRepository.findAll().stream()
                .filter(e -> e.getCategory() == ExerciseCategory.LEGS)
                .count()).isEqualTo(3);

        // 3. Drafts appear immediately in Admin > Exercises > Draft and stats reflect them
        mockMvc.perform(get("/api/v1/admin/exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "DRAFT")
                        .param("origin", "AI_RECOMMENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));

        mockMvc.perform(get("/api/v1/admin/exercises/stats")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(4))
                .andExpect(jsonPath("$.publishedCount").value(1))
                .andExpect(jsonPath("$.draftCount").value(3))
                .andExpect(jsonPath("$.aiRecommendedCount").value(3));

        // 4. Draft exercises remain completely hidden from regular users
        Long firstDraftId = chatResp.getCreatedExercises().get(0).id();
        MvcResult publicListRes = mockMvc.perform(get("/api/v1/exercises")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode publicArr = objectMapper.readTree(publicListRes.getResponse().getContentAsString());
        assertThat(publicArr.size()).isEqualTo(1);
        assertThat(publicArr.get(0).get("name").asText()).isEqualTo("Push-Up");

        mockMvc.perform(get("/api/v1/exercises/" + firstDraftId)
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isNotFound());

        // 5. Regular user cannot publish or bulk-publish exercises
        mockMvc.perform(patch("/api/v1/admin/exercises/" + firstDraftId + "/status")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isForbidden());

        // 6. Admin publishes one draft individually and the rest in bulk
        mockMvc.perform(patch("/api/v1/admin/exercises/" + firstDraftId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"PUBLISHED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.origin").value("AI_RECOMMENDED"));

        // Public catalog now exposes Push-Up + first published leg exercise
        mockMvc.perform(get("/api/v1/exercises")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        // Bulk publish remaining 2 drafts
        List<Long> remainingIds = chatResp.getCreatedExercises().stream()
                .map(ex -> ex.id())
                .filter(id -> !id.equals(firstDraftId))
                .toList();
        mockMvc.perform(patch("/api/v1/admin/exercises/bulk-status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("exerciseIds", remainingIds, "status", "PUBLISHED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/v1/exercises")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4));

        // Returning an exercise back to DRAFT hides it from regular users again
        mockMvc.perform(patch("/api/v1/admin/exercises/" + firstDraftId + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DRAFT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/v1/exercises")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }

    @Test
    @DisplayName("When persistence fails (exhausted/no new unique candidates or DB failure), AI returns explicit error and never falsely claims success")
    void persistenceFailureDoesNotFalselyClaimSuccess() throws Exception {
        // Pre-create every single canonical leg exercise so creating more cannot persist duplicates
        List<String> preExistingLegNames = List.of(
                "Bodyweight Squat", "Pistol Squat", "Bulgarian Split Squat", "Cossack Squat",
                "Assisted Pistol Squat", "Shrimp Squat", "Nordic Hamstring Curl",
                "Single-Leg Calf Raise", "Sissy Squat", "Single-Leg Glute Bridge",
                "Explosive Jump Squat", "Reverse Nordic Curl"
        );
        int idx = 1;
        for (String name : preExistingLegNames) {
            exerciseRepository.save(Exercise.builder()
                    .name(name)
                    .code("LEG_EX_" + (idx++))
                    .category(ExerciseCategory.LEGS)
                    .difficulty(DifficultyLevel.BEGINNER)
                    .status(ExerciseStatus.PUBLISHED)
                    .origin(ExerciseOrigin.MANUAL)
                    .active(true)
                    .build());
        }

        AdminChatRequest req = AdminChatRequest.builder()
                .message("Add 3 leg exercises")
                .build();

        MvcResult res = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EXERCISE_DRAFT_CREATION"))
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(false))
                .andReturn();

        AdminChatResponse chatResp = objectMapper.readValue(res.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(chatResp.getAssistantMessage().getContent()).contains("Failed to Save Exercise Drafts");
        assertThat(chatResp.getAssistantMessage().getContent()).doesNotContain("Added 3 Exercise");
    }
}
