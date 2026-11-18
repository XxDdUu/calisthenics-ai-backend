package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.repository.AdminAiMessageRepository;
import com.calisthenics.backend.adminai.repository.BulkExerciseGenerationJobRepository;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiReliabilityAndIdempotencyTest {

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
    @Autowired private AdminAiMessageRepository messageRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private BulkExerciseGenerationJobRepository bulkJobRepository;

    @MockBean private GeminiApiClient geminiApiClient;

    private String adminToken;
    private String userToken;

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();
        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.callAdminAgent(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.isConfigured()).thenReturn(false);

        long ts = System.currentTimeMillis();
        User admin = userRepository.save(User.builder()
                .email("admin_idemp_" + ts + "@test.com")
                .fullName("Reliability Admin")
                .role(Role.ROLE_ADMIN)
                .build());
        User regular = userRepository.save(User.builder()
                .email("user_idemp_" + ts + "@test.com")
                .fullName("Normal User")
                .role(Role.ROLE_USER)
                .build());

        adminToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(admin));
        userToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(regular));
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    @Test
    @DisplayName("First message succeeds cleanly without retry; identical Idempotency-Key replay returns exact cached response without duplicate messages")
    void firstMessageAndIdempotentReplay_neverDuplicatesMessages() throws Exception {
        AdminChatRequest req = AdminChatRequest.builder()
                .message("Summarize platform activity this month.")
                .build();

        MvcResult first = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "first-msg-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").isNumber())
                .andExpect(jsonPath("$.assistantMessage.content").isNotEmpty())
                .andReturn();

        JsonNode firstJson = objectMapper.readTree(first.getResponse().getContentAsString());
        long convId = firstJson.path("conversationId").asLong();
        long firstUserMsgId = firstJson.path("userMessage").path("id").asLong();
        long firstAssistantMsgId = firstJson.path("assistantMessage").path("id").asLong();
        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(convId)).hasSize(2);

        // Retry with same Idempotency-Key (simulating timeout lost-response recovery)
        MvcResult retry = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .header("Idempotency-Key", "first-msg-key-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId))
                .andExpect(jsonPath("$.userMessage.id").value(firstUserMsgId))
                .andExpect(jsonPath("$.assistantMessage.id").value(firstAssistantMsgId))
                .andReturn();

        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(convId)).hasSize(2);

        // Second distinct prompt on same conversation gets distinct key and appends cleanly
        AdminChatRequest secondReq = AdminChatRequest.builder()
                .conversationId(convId)
                .message("How many users have active coaches?")
                .idempotencyKey("second-msg-key-002")
                .build();

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convId));

        assertThat(messageRepository.findByConversationIdOrderByCreatedAtAsc(convId)).hasSize(4);
    }

    @Test
    @DisplayName("Idempotent retry on draft & bulk exercise creation never duplicates database exercises or jobs")
    void idempotentRetryOnExerciseCreation_preventsDuplicates() throws Exception {
        AdminChatRequest draftReq = AdminChatRequest.builder()
                .message("Add 3 leg exercises: 1. Box Pistol Squat — LEGS — INTERMEDIATE 2. Cossack Squat — LEGS — INTERMEDIATE 3. Shrimp Squat — LEGS — ADVANCED")
                .idempotencyKey("draft-key-001")
                .build();

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdExercises.length()").value(3));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(draftReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdExercises.length()").value(3));

        assertThat(exerciseRepository.findAll().stream()
                .filter(e -> e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED).count()).isEqualTo(3);

        AdminChatRequest bulkReq = AdminChatRequest.builder()
                .message("Generate 50 new calisthenics exercises.")
                .idempotencyKey("bulk-idemp-001")
                .build();

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulkGenerationJob.savedCount").value(50));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bulkGenerationJob.savedCount").value(50));

        assertThat(bulkJobRepository.count()).isEqualTo(1L);
        assertThat(exerciseRepository.findAll().stream()
                .filter(e -> e.getOrigin() == ExerciseOrigin.AI_RECOMMENDED).count()).isEqualTo(53);

        // Non-admin receives 403
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isForbidden());
    }
}
