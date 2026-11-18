package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.AdminChatResponse;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecuteRequest;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftCandidateDto;
import com.calisthenics.backend.adminai.dto.data.GeminiAdminToolPlan;
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

    @Test
    @DisplayName("Explicit 3 leg exercises create exact DB records even if prior conversation discussed Front Lever or stale skill_keyword is returned")
    void explicitThreeLegExercisesCreateExactRecordsIgnoringStaleFrontLeverContext() throws Exception {
        // Turn 1: Prior Front Lever recommendation turn in the SAME conversation
        AdminChatRequest frontLeverTurn = AdminChatRequest.builder()
                .message("What exercises should we add for Front Lever?")
                .build();
        MvcResult turn1Res = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(frontLeverTurn)))
                .andExpect(status().isOk())
                .andReturn();
        Long conversationId = objectMapper.readValue(
                turn1Res.getResponse().getContentAsString(), AdminChatResponse.class).getConversationId();

        // Simulate stale Gemini tool planner returning skill_keyword="Front Lever" on Turn 2 (without mismatched drafts)
        GeminiAdminToolPlan staleSkillPlan = new GeminiAdminToolPlan();
        staleSkillPlan.setIntent("EXERCISE_DRAFT_CREATION");
        staleSkillPlan.setTools(List.of("create_exercise_drafts"));
        staleSkillPlan.setSkillKeyword("Front Lever");
        staleSkillPlan.setExperienceLevel("BEGINNER");
        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.of(staleSkillPlan));

        // Turn 2: Explicit request matching exact bug reproduction prompt
        String explicitLegPrompt = """
                Give me more recommended exercises of legs. Add these 3 exercises:
                1. Box Pistol Squat — LEGS — INTERMEDIATE
                2. Nordic Hamstring Curl (Negative) — LEGS — ADVANCED
                3. Cossack Squat — LEGS — INTERMEDIATE""";

        MvcResult turn2Res = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message(explicitLegPrompt)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EXERCISE_DRAFT_CREATION"))
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.createdExercises.length()").value(3))
                .andReturn();

        AdminChatResponse resp = objectMapper.readValue(turn2Res.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(resp.getCreatedExercises()).extracting(e -> e.name()).containsExactly(
                "Box Pistol Squat",
                "Nordic Hamstring Curl (Negative)",
                "Cossack Squat"
        );
        assertThat(resp.getCreatedExercises()).allSatisfy(ex -> {
            assertThat(ex.id()).isNotNull();
            assertThat(ex.category()).isEqualTo(ExerciseCategory.LEGS);
            assertThat(ex.status()).isEqualTo(ExerciseStatus.DRAFT);
            assertThat(ex.origin()).isEqualTo(ExerciseOrigin.AI_RECOMMENDED);
            assertThat(ex.active()).isFalse();
            assertThat(ex.description()).isNotBlank();
            assertThat(ex.instructions()).isNotBlank();
            assertThat(ex.targetMuscles()).isNotBlank();
            assertThat(ex.equipment()).isNotBlank();
        });
        assertThat(resp.getCreatedExercises().get(0).difficulty()).isEqualTo(DifficultyLevel.INTERMEDIATE);
        assertThat(resp.getCreatedExercises().get(1).difficulty()).isEqualTo(DifficultyLevel.ADVANCED);
        assertThat(resp.getCreatedExercises().get(2).difficulty()).isEqualTo(DifficultyLevel.INTERMEDIATE);

        // Verify actual DB state matches exact requested names, categories, difficulties, statuses, and NEVER Front Lever
        List<Exercise> allExercises = exerciseRepository.findAll();
        assertThat(allExercises).hasSize(4); // Push-Up + exact 3 leg drafts
        assertThat(allExercises).noneMatch(e ->
                e.getName().contains("Front Lever") || e.getName().contains("German Hang"));

        // Verify AI response cites actual persisted names, DB IDs, categories, difficulties, and DRAFT status
        String reply = resp.getAssistantMessage().getContent();
        assertThat(reply)
                .contains("Box Pistol Squat")
                .contains("Nordic Hamstring Curl (Negative)")
                .contains("Cossack Squat")
                .contains("ID #" + resp.getCreatedExercises().get(0).id())
                .contains("ID #" + resp.getCreatedExercises().get(1).id())
                .contains("ID #" + resp.getCreatedExercises().get(2).id())
                .contains("LEGS")
                .contains("ADVANCED")
                .contains("INTERMEDIATE")
                .contains("DRAFT");

        // Verify Admin > Exercises > Draft lists all 3, and regular user catalog still hides them
        mockMvc.perform(get("/api/v1/admin/exercises")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("status", "DRAFT")
                        .param("origin", "AI_RECOMMENDED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3));
        mockMvc.perform(get("/api/v1/exercises")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("Validation rejects AI Front Lever substitution and creates ZERO wrong records; corrective regeneration succeeds with exact leg exercises")
    void validationBlocksFrontLeverSubstitutionAndSupportsValidatedToolArguments() throws Exception {
        String explicitLegPrompt = """
                Give me more recommended exercises of legs. Add these 3 exercises:
                1. Box Pistol Squat — LEGS — INTERMEDIATE
                2. Nordic Hamstring Curl (Negative) — LEGS — ADVANCED
                3. Cossack Squat — LEGS — INTERMEDIATE""";

        GeminiAdminToolPlan badFrontLeverPlan = new GeminiAdminToolPlan();
        badFrontLeverPlan.setIntent("EXERCISE_DRAFT_CREATION");
        badFrontLeverPlan.setTools(List.of("create_exercise_drafts"));
        badFrontLeverPlan.setExerciseCategory("SKILL");
        badFrontLeverPlan.setExerciseDrafts(List.of(
                ExerciseDraftCandidateDto.builder()
                        .name("Straddle Front Lever").code("STRADDLE_FRONT_LEVER")
                        .category("SKILL").difficulty("ADVANCED")
                        .description("Horizontal straddle hold").instructions("Hold flat")
                        .targetMuscles("Lats").equipment("Pull-Up Bar").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Front Lever Raises").code("FRONT_LEVER_RAISES")
                        .category("PULL").difficulty("INTERMEDIATE")
                        .description("Straight arm pulls").instructions("Pull horizontal")
                        .targetMuscles("Lats").equipment("Pull-Up Bar").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("German Hang").code("GERMAN_HANG")
                        .category("ISOMETRIC").difficulty("BEGINNER")
                        .description("Shoulder extension hang").instructions("Rotate back")
                        .targetMuscles("Anterior Delts").equipment("Rings").build()
        ));

        // Scenario A: Even after corrective retry, AI keeps returning Front Lever -> MUST reject cleanly and insert ZERO rows!
        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.of(badFrontLeverPlan));

        MvcResult failRes = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message(explicitLegPrompt)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(false))
                .andExpect(jsonPath("$.exerciseDraftResult.validationFailed").value(true))
                .andExpect(jsonPath("$.createdExercises.length()").value(0))
                .andReturn();

        AdminChatResponse failBody = objectMapper.readValue(failRes.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(failBody.getAssistantMessage().getContent())
                .contains("Failed to Save Exercise Drafts")
                .contains("Validation failed")
                .doesNotContain("Added 3 Exercise");
        assertThat(exerciseRepository.findAll()).hasSize(1); // Only initial Push-Up; ZERO Front Lever rows created!

        // Direct tool execution with mismatched Front Lever arguments also rejected cleanly (422, zero DB writes)
        AdminToolExecuteRequest badToolReq = AdminToolExecuteRequest.builder()
                .toolName("create_exercise_drafts")
                .instruction(explicitLegPrompt)
                .exerciseCategory(ExerciseCategory.LEGS)
                .requestedCount(3)
                .exerciseDrafts(badFrontLeverPlan.getExerciseDrafts())
                .build();
        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badToolReq)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(exerciseRepository.findAll()).hasSize(1);

        // Scenario B: First AI plan substitutes Front Lever; corrective regeneration returns exact requested leg drafts
        GeminiAdminToolPlan validLegPlan = new GeminiAdminToolPlan();
        validLegPlan.setIntent("EXERCISE_DRAFT_CREATION");
        validLegPlan.setTools(List.of("create_exercise_drafts"));
        validLegPlan.setExerciseCategory("LEGS");
        validLegPlan.setRequestedCount(3);
        validLegPlan.setExerciseDrafts(List.of(
                ExerciseDraftCandidateDto.builder()
                        .name("Box Pistol Squat").code("BOX_PISTOL_SQUAT")
                        .category("LEGS").difficulty("INTERMEDIATE")
                        .description("Box-assisted pistol squat").instructions("Touch box gently and stand")
                        .targetMuscles("Quadriceps, Glutes").equipment("Plyo Box").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Nordic Hamstring Curl (Negative)").code("NORDIC_HAMSTRING_CURL_NEGATIVE")
                        .category("LEGS").difficulty("ADVANCED")
                        .description("Slow eccentric Nordic lowering").instructions("Lower slowly for 4-5s")
                        .targetMuscles("Hamstrings, Glutes").equipment("Anchored Strap").build(),
                ExerciseDraftCandidateDto.builder()
                        .name("Cossack Squat").code("COSSACK_SQUAT")
                        .category("LEGS").difficulty("INTERMEDIATE")
                        .description("Lateral deep single-leg squat").instructions("Shift weight side to side")
                        .targetMuscles("Quadriceps, Adductors").equipment("Floor").build()
        ));

        when(geminiApiClient.planAdminTools(anyString(), anyList()))
                .thenReturn(Optional.of(badFrontLeverPlan))
                .thenReturn(Optional.of(validLegPlan));

        MvcResult recoveredRes = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message(explicitLegPrompt)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(true))
                .andExpect(jsonPath("$.createdExercises.length()").value(3))
                .andReturn();

        AdminChatResponse recovered = objectMapper.readValue(
                recoveredRes.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(recovered.getCreatedExercises()).extracting(e -> e.name()).containsExactly(
                "Box Pistol Squat", "Nordic Hamstring Curl (Negative)", "Cossack Squat"
        );
        // Verify persisted DB record fields match validated tool arguments cleanly
        Exercise savedNordic = exerciseRepository.findByNameIgnoreCase("Nordic Hamstring Curl (Negative)").orElseThrow();
        assertThat(savedNordic.getCategory()).isEqualTo(ExerciseCategory.LEGS);
        assertThat(savedNordic.getDifficulty()).isEqualTo(DifficultyLevel.ADVANCED);
        assertThat(savedNordic.getDescription()).isEqualTo("Slow eccentric Nordic lowering");
        assertThat(savedNordic.getEquipment()).isEqualTo("Anchored Strap");
        assertThat(savedNordic.getStatus()).isEqualTo(ExerciseStatus.DRAFT);
        assertThat(savedNordic.getOrigin()).isEqualTo(ExerciseOrigin.AI_RECOMMENDED);
    }

    @Test
    @DisplayName("Recommendations + follow-ups: category recommendations work and topic-switching follow-ups do not reuse stale exercise lists")
    void categoryRecommendationsAndFollowUpIsolation() throws Exception {
        // 1. Conversational leg recommendation query returns LEGS recommendations without creating exercises yet
        MvcResult recRes = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Give me more recommended exercises of legs")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EXERCISE_RECOMMENDATION"))
                .andReturn();

        AdminChatResponse recBody = objectMapper.readValue(recRes.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(recBody.getRecommendations()).isNotEmpty();
        assertThat(recBody.getRecommendations()).allSatisfy(r ->
                assertThat(r.getExerciseDetails().getCategory()).isEqualTo(ExerciseCategory.LEGS));
        assertThat(exerciseRepository.findAll()).hasSize(1);

        // 2. Switching topic in the same conversation to CORE must add CORE exercises, NEVER stale LEGS exercises
        MvcResult coreRes = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(recBody.getConversationId())
                                .message("Add 2 core exercises to draft")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("EXERCISE_DRAFT_CREATION"))
                .andExpect(jsonPath("$.createdExercises.length()").value(2))
                .andReturn();

        AdminChatResponse coreBody = objectMapper.readValue(coreRes.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(coreBody.getCreatedExercises()).allSatisfy(ex ->
                assertThat(ex.category()).isEqualTo(ExerciseCategory.CORE));

        // 3. Ambiguous "Add these 3 exercises" AFTER a non-recommendation turn rejects cleanly instead of pulling stale Front Lever exercises
        MvcResult ambiguousRes = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(recBody.getConversationId())
                                .message("Add these 3 exercises")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exerciseDraftResult.success").value(false))
                .andExpect(jsonPath("$.createdExercises.length()").value(0))
                .andReturn();

        AdminChatResponse ambiguousBody = objectMapper.readValue(
                ambiguousRes.getResponse().getContentAsString(), AdminChatResponse.class);
        assertThat(ambiguousBody.getAssistantMessage().getContent()).contains("Failed to Save Exercise Drafts");
        assertThat(exerciseRepository.findAll()).noneMatch(e -> e.getName().contains("Front Lever"));
    }
}
