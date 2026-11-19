package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.calisthenics.backend.aichat.dto.CreateAiConversationRequest;
import com.calisthenics.backend.aichat.dto.SendAiMessageRequest;
import com.calisthenics.backend.aichat.dto.preview.WorkoutPlanChangePreviewActionResponse;
import com.calisthenics.backend.aichat.entity.AiWorkoutPlanChangePreview;
import com.calisthenics.backend.aichat.repository.AiWorkoutPlanChangePreviewRepository;
import com.calisthenics.backend.aichat.type.WorkoutPlanPreviewStatus;
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
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
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

import java.nio.charset.StandardCharsets;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPlanPreviewConfirmationIntegrationTest {

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
            "ai_workout_plan_change_previews",
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
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Autowired private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    @Autowired private AiWorkoutPlanChangePreviewRepository previewRepository;

    @MockBean private GeminiApiClient geminiApiClient;

    private User athlete;
    private User otherUser;
    private String athleteToken;
    private String otherToken;

    private Exercise pushUp;
    private Exercise tuckPlanche;
    private Exercise frontLever;
    private Exercise frontLeverRaises;
    private Exercise pistolSquat;
    private WorkoutPlan workoutPlan;

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();

        long ts = System.currentTimeMillis();
        athlete = userRepository.save(User.builder()
                .email("preview_athlete_" + ts + "@test.com")
                .fullName("Preview Athlete")
                .role(Role.ROLE_USER)
                .build());
        otherUser = userRepository.save(User.builder()
                .email("other_athlete_" + ts + "@test.com")
                .fullName("Other Athlete")
                .role(Role.ROLE_USER)
                .build());

        athleteToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athlete));
        otherToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(otherUser));

        pushUp = exerciseRepository.save(Exercise.builder()
                .name("Push-Up").code("PUSH_UP").category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER).status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL).active(true).build());
        tuckPlanche = exerciseRepository.save(Exercise.builder()
                .name("Tuck Planche").code("TUCK_PLANCHE").category(ExerciseCategory.ISOMETRIC)
                .difficulty(DifficultyLevel.INTERMEDIATE).status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL).active(true).build());
        frontLever = exerciseRepository.save(Exercise.builder()
                .name("Front Lever").code("FRONT_LEVER").category(ExerciseCategory.SKILL)
                .difficulty(DifficultyLevel.ADVANCED).status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL).active(true).build());
        frontLeverRaises = exerciseRepository.save(Exercise.builder()
                .name("Front Lever Raises").code("FRONT_LEVER_RAISES").category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.INTERMEDIATE).status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL).active(true).build());
        pistolSquat = exerciseRepository.save(Exercise.builder()
                .name("Pistol Squat").code("PISTOL_SQUAT").category(ExerciseCategory.LEGS)
                .difficulty(DifficultyLevel.INTERMEDIATE).status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL).active(true).build());

        workoutPlan = buildTestPlan();
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private WorkoutPlan buildTestPlan() {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athlete)
                .name("Push Pull Skills")
                .description("Calisthenics strength & lever split")
                .splitType(WorkoutSplitType.PPL)
                .active(true)
                .build();

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(dow == WorkoutDayOfWeek.SUNDAY)
                    .sessionType(switch (dow) {
                        case MONDAY, THURSDAY -> WorkoutSessionType.PUSH;
                        case TUESDAY, FRIDAY -> WorkoutSessionType.PULL;
                        case WEDNESDAY, SATURDAY -> WorkoutSessionType.LEGS;
                        case SUNDAY -> WorkoutSessionType.REST;
                    })
                    .build();

            if (dow == WorkoutDayOfWeek.MONDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pushUp)
                        .sets(4).reps(12).restSeconds(90).displayOrder(1).build());
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(frontLeverRaises)
                        .sets(3).reps(10).restSeconds(90).displayOrder(2).notes("Controlled eccentric").build());
            } else if (dow == WorkoutDayOfWeek.TUESDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(frontLever)
                        .sets(4).durationSeconds(15).restSeconds(120).displayOrder(1).build());
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(tuckPlanche)
                        .sets(3).durationSeconds(15).restSeconds(90).displayOrder(2).build());
            } else if (dow == WorkoutDayOfWeek.WEDNESDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pistolSquat)
                        .sets(3).reps(8).restSeconds(90).displayOrder(1).build());
            }
            plan.getDays().add(day);
        }
        return workoutPlanRepository.save(plan);
    }

    private Long createConversation(String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAiConversationRequest("Preview Flow"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private AiMessageResponse sendMessage(String token, Long convId, String message) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendAiMessageRequest(message, "UTC"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(StandardCharsets.UTF_8), AiMessageResponse.class);
    }

    private String extractPreviewId(AiMessageResponse msg) throws Exception {
        JsonNode root = objectMapper.readTree(msg.getMetadata());
        return root.path("previewId").asText();
    }

    private List<WorkoutPlanExercise> dayExercises(WorkoutDayOfWeek dow) {
        WorkoutPlanDay day = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(workoutPlan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == dow)
                .findFirst().orElseThrow();
        return workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId());
    }

    private List<String> dayExerciseNames(WorkoutDayOfWeek dow) {
        return dayExercises(dow).stream().map(pe -> pe.getExercise().getName()).toList();
    }

    @Test
    @DisplayName("Scenario A: Move and update previews first with ZERO DB mutation, then executes atomically on Confirm button and prevents replay")
    void scenarioA_moveAndUpdate_previewThenConfirmAtomically() throws Exception {
        Long convId = createConversation(athleteToken);

        // 1. User requests move + sets change (implicit Monday source day!)
        AiMessageResponse previewMsg = sendMessage(
                athleteToken, convId, "Move Front Lever Raises to Tuesday and make it 4 sets.");
        assertThat(previewMsg.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(previewMsg.getContent()).contains("Workout Plan Preview");
        assertThat(previewMsg.getContent()).contains("Monday — PUSH");
        assertThat(previewMsg.getContent()).contains("REMOVE: Front Lever Raises — 3 sets × 10 reps · Rest 90s");
        assertThat(previewMsg.getContent()).contains("Tuesday — PULL");
        assertThat(previewMsg.getContent()).contains("ADD: Front Lever Raises — 4 sets × 10 reps · Rest 90s");

        String previewId = extractPreviewId(previewMsg);
        assertThat(previewId).isNotBlank();

        // 2. Verify NO database mutation occurred before confirmation!
        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY))
                .containsExactly("Push-Up", "Front Lever Raises");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY))
                .containsExactly("Pistol Squat");

        // Verify GET /previews/pending returns the active preview
        mockMvc.perform(get("/api/v1/ai/conversations/" + convId + "/previews/pending")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(previewId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.affectedDays.length()").value(2));

        // 3. Another user cannot confirm this preview (404)
        mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/previews/" + previewId + "/confirm")
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // 4. Confirm via endpoint -> atomic execution + verification
        MvcResult confirmRes = mockMvc.perform(
                        post("/api/v1/ai/conversations/" + convId + "/previews/" + previewId + "/confirm")
                                .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.mutated").value(true))
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.preview.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.assistantMessage.content").exists())
                .andReturn();

        // 5. Verify final DB state matches preview exactly (and unaffected days untouched!)
        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY))
                .containsExactly("Push-Up");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY))
                .containsExactly("Pistol Squat");

        WorkoutPlanExercise moved = dayExercises(WorkoutDayOfWeek.TUESDAY).stream()
                .filter(pe -> pe.getExercise().getName().equals("Front Lever Raises"))
                .findFirst().orElseThrow();
        assertThat(moved.getSets()).isEqualTo(4);
        assertThat(moved.getReps()).isEqualTo(10);
        assertThat(moved.getRestSeconds()).isEqualTo(90);
        assertThat(moved.getNotes()).isEqualTo("Controlled eccentric");

        // 6. Replay / duplicate confirmation rejected with 409 and does NOT duplicate exercise
        mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/previews/" + previewId + "/confirm")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_ALREADY_RESOLVED"))
                .andExpect(jsonPath("$.mutated").value(false));

        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");

        // 7. Reloading conversation hydrates preview card metadata to CONFIRMED
        MvcResult convRes = mockMvc.perform(get("/api/v1/ai/conversations/" + convId)
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(convRes.getResponse().getContentAsString()).contains("\\\"status\\\":\\\"CONFIRMED\\\"");
    }

    @Test
    @DisplayName("Scenario B: Cancel via chat message and Cancel button discard pending preview without modifying workout plan")
    void scenarioB_cancel_leavesWorkoutPlanUnchanged() throws Exception {
        Long convId = createConversation(athleteToken);

        // B1: Cancel via conversational "Cancel."
        AiMessageResponse preview1 = sendMessage(athleteToken, convId, "Move Front Lever Raises to Tuesday.");
        String previewId1 = extractPreviewId(preview1);

        AiMessageResponse cancelMsg = sendMessage(athleteToken, convId, "Cancel.");
        assertThat(cancelMsg.getContent()).contains("Cancelled");
        assertThat(cancelMsg.getMetadata()).contains("\"mutated\":false");

        assertThat(previewRepository.findByPreviewIdAndUserId(previewId1, athlete.getId()))
                .get().extracting(AiWorkoutPlanChangePreview::getStatus)
                .isEqualTo(WorkoutPlanPreviewStatus.CANCELLED);
        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY))
                .containsExactly("Push-Up", "Front Lever Raises");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche");

        // B2: Cancel via button endpoint
        AiMessageResponse preview2 = sendMessage(athleteToken, convId, "Remove Push-Up from Monday.");
        String previewId2 = extractPreviewId(preview2);

        mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/previews/" + previewId2 + "/cancel")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.mutated").value(false))
                .andExpect(jsonPath("$.preview.status").value("CANCELLED"));

        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY))
                .containsExactly("Push-Up", "Front Lever Raises");
    }

    @Test
    @DisplayName("Scenario C: Follow-up message revises pending preview to 5 sets, supersedes Rev 1, and Confirm executes latest revision")
    void scenarioC_modifyPendingPreviewAndConfirmLatest() throws Exception {
        Long convId = createConversation(athleteToken);

        // Turn 1: Move Front Lever Raises to Tuesday
        AiMessageResponse rev1Msg = sendMessage(athleteToken, convId, "Move Front Lever Raises to Tuesday.");
        String rev1Id = extractPreviewId(rev1Msg);
        assertThat(rev1Msg.getContent()).contains("3 sets × 10 reps");

        // Turn 2: Revise to 5 sets instead -> still ZERO DB mutation!
        AiMessageResponse rev2Msg = sendMessage(athleteToken, convId, "Make it 5 sets instead.");
        String rev2Id = extractPreviewId(rev2Msg);
        assertThat(rev2Id).isNotEqualTo(rev1Id);
        assertThat(rev2Msg.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(rev2Msg.getContent()).contains("Rev 2");
        assertThat(rev2Msg.getContent()).contains("5 sets × 10 reps · Rest 90s");
        assertThat(rev2Msg.getMetadata()).contains("\"supersedesPreviewId\":\"" + rev1Id + "\"");

        // Still ZERO DB changes
        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY)).containsExactly("Push-Up", "Front Lever Raises");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY)).containsExactly("Front Lever", "Tuck Planche");

        // Attempting to confirm superseded Rev 1 is rejected with 409!
        mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/previews/" + rev1Id + "/confirm")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_ALREADY_RESOLVED"));

        // Turn 3: Confirm via chat -> executes Rev 2 (5 sets)!
        AiMessageResponse confirmMsg = sendMessage(athleteToken, convId, "Confirm.");
        assertThat(confirmMsg.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(confirmMsg.getMetadata()).contains("\"mutated\":true");

        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY)).containsExactly("Push-Up");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");

        WorkoutPlanExercise moved = dayExercises(WorkoutDayOfWeek.TUESDAY).stream()
                .filter(pe -> pe.getExercise().getName().equals("Front Lever Raises"))
                .findFirst().orElseThrow();
        assertThat(moved.getSets()).isEqualTo(5);
        assertThat(moved.getReps()).isEqualTo(10);
        assertThat(moved.getRestSeconds()).isEqualTo(90);
    }

    @Test
    @DisplayName("Scenario D: Duplicate prevention rejects duplicate add and offers UPDATE only when user requests changes")
    void scenarioD_duplicatePreventionAndConditionalUpdateOffer() throws Exception {
        Long convId = createConversation(athleteToken);

        // Front Lever Raises is on Monday; first move it to Tuesday and confirm
        sendMessage(athleteToken, convId, "Move Front Lever Raises to Tuesday.");
        sendMessage(athleteToken, convId, "Confirm.");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");

        // D1: "Add Front Lever Raises to Tuesday." (no changes) -> explain already present, NO duplicate, NO preview!
        AiMessageResponse dupResp = sendMessage(athleteToken, convId, "Add Front Lever Raises to Tuesday.");
        assertThat(dupResp.getMetadata()).contains("\"mutated\":false");
        assertThat(dupResp.getMetadata()).contains("\"error\":\"ALREADY_EXISTS\"");
        assertThat(dupResp.getContent()).contains("already scheduled on **Tuesday**");
        mockMvc.perform(get("/api/v1/ai/conversations/" + convId + "/previews/pending")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isNoContent());

        // D2: "Add Front Lever Raises 4 sets to Tuesday." (requested change!) -> offers UPDATE_EXERCISE preview!
        AiMessageResponse updateOffer = sendMessage(athleteToken, convId, "Add Front Lever Raises 4 sets to Tuesday.");
        assertThat(updateOffer.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(updateOffer.getMetadata()).contains("UPDATE_EXERCISE");
        assertThat(updateOffer.getContent()).contains("UPDATE: Front Lever Raises");

        // Confirm update -> still exactly ONE Front Lever Raises on Tuesday, now with 4 sets!
        sendMessage(athleteToken, convId, "Confirm.");
        List<WorkoutPlanExercise> tueExercises = dayExercises(WorkoutDayOfWeek.TUESDAY);
        assertThat(tueExercises.stream().filter(pe -> pe.getExercise().getName().equals("Front Lever Raises")).count())
                .isEqualTo(1);
        WorkoutPlanExercise updated = tueExercises.stream()
                .filter(pe -> pe.getExercise().getName().equals("Front Lever Raises"))
                .findFirst().orElseThrow();
        assertThat(updated.getSets()).isEqualTo(4);
        assertThat(updated.getReps()).isEqualTo(10);
    }

    @Test
    @DisplayName("Scenario E: Stale preview rejects confirmation, never overwrites newer workout data, refreshes plan and generates new preview")
    void scenarioE_stalePreviewRejectedAndRefreshed() throws Exception {
        Long convId = createConversation(athleteToken);

        // 1. Generate preview: Move Front Lever Raises to Tuesday and make it 4 sets (currently 3x10)
        AiMessageResponse previewMsg = sendMessage(
                athleteToken, convId, "Move Front Lever Raises to Tuesday and make it 4 sets.");
        String stalePreviewId = extractPreviewId(previewMsg);

        // 2. Externally mutate workout plan in DB (change Monday Front Lever Raises reps from 10 to 12!)
        WorkoutPlanExercise mondayFlr = dayExercises(WorkoutDayOfWeek.MONDAY).stream()
                .filter(pe -> pe.getExercise().getName().equals("Front Lever Raises"))
                .findFirst().orElseThrow();
        mondayFlr.setReps(12);
        workoutPlanExerciseRepository.saveAndFlush(mondayFlr);

        // 3. Attempt to confirm the stale preview -> MUST return 409 Conflict with stale=true + refreshedPreview!
        MvcResult staleRes = mockMvc.perform(
                        post("/api/v1/ai/conversations/" + convId + "/previews/" + stalePreviewId + "/confirm")
                                .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.mutated").value(false))
                .andExpect(jsonPath("$.stale").value(true))
                .andExpect(jsonPath("$.code").value("STALE_PREVIEW"))
                .andExpect(jsonPath("$.refreshedPreview.previewId").isNotEmpty())
                .andExpect(jsonPath("$.refreshedPreview.status").value("PENDING"))
                .andReturn();

        // Verify stale confirmation did NOT mutate the DB (Monday still has Front Lever Raises with 12 reps!)
        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY)).containsExactly("Push-Up", "Front Lever Raises");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY)).containsExactly("Front Lever", "Tuck Planche");
        WorkoutPlanExercise stillOnMonday = dayExercises(WorkoutDayOfWeek.MONDAY).stream()
                .filter(pe -> pe.getExercise().getName().equals("Front Lever Raises"))
                .findFirst().orElseThrow();
        assertThat(stillOnMonday.getReps()).isEqualTo(12);

        // 4. Confirm the newly generated refreshed preview -> applies 4 sets while preserving the newer 12 reps!
        WorkoutPlanChangePreviewActionResponse staleBody = objectMapper.readValue(
                staleRes.getResponse().getContentAsString(), WorkoutPlanChangePreviewActionResponse.class);
        String refreshedId = staleBody.getRefreshedPreview().getPreviewId();
        assertThat(refreshedId).isNotEqualTo(stalePreviewId);

        mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/previews/" + refreshedId + "/confirm")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.mutated").value(true));

        WorkoutPlanExercise refreshedMoved = dayExercises(WorkoutDayOfWeek.TUESDAY).stream()
                .filter(pe -> pe.getExercise().getName().equals("Front Lever Raises"))
                .findFirst().orElseThrow();
        assertThat(refreshedMoved.getSets()).isEqualTo(4);
        assertThat(refreshedMoved.getReps()).isEqualTo(12);
        assertThat(refreshedMoved.getRestSeconds()).isEqualTo(90);
    }

    @Test
    @DisplayName("Compound multi-op atomic execution, UPDATE_EXERCISE, REORDER_EXERCISES, and TTL expiration")
    void compoundMultiOpReorderUpdateAndExpiration() throws Exception {
        Long convId = createConversation(athleteToken);

        // 1. Compound multi-op: remove Tuck Planche from Tuesday and add Pistol Squat to Tuesday atomically
        AiMessageResponse compoundPreview = sendMessage(
                athleteToken, convId, "Remove Tuck Planche from Tuesday and add Pistol Squat to Tuesday.");
        assertThat(compoundPreview.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(compoundPreview.getContent()).contains("REMOVE: Tuck Planche");
        assertThat(compoundPreview.getContent()).contains("ADD: Pistol Squat");

        // Zero DB changes before confirm
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY)).containsExactly("Front Lever", "Tuck Planche");

        sendMessage(athleteToken, convId, "Confirm.");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY)).containsExactly("Front Lever", "Pistol Squat");

        // 2. REORDER_EXERCISES: Put Pistol Squat first on Tuesday
        AiMessageResponse reorderPreview = sendMessage(athleteToken, convId, "Put Pistol Squat first on Tuesday.");
        assertThat(reorderPreview.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(reorderPreview.getMetadata()).contains("REORDER_EXERCISES");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY)).containsExactly("Front Lever", "Pistol Squat");

        sendMessage(athleteToken, convId, "Confirm.");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.TUESDAY)).containsExactly("Pistol Squat", "Front Lever");

        // 3. UPDATE_EXERCISE: Update Push-Up on Monday to 5 sets x 15 reps, 60s rest
        AiMessageResponse updatePreview = sendMessage(
                athleteToken, convId, "Update Push-Up on Monday to 5 sets x 15 reps, 60s rest.");
        assertThat(updatePreview.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(updatePreview.getMetadata()).contains("UPDATE_EXERCISE");
        sendMessage(athleteToken, convId, "Confirm.");
        WorkoutPlanExercise updatedPushUp = dayExercises(WorkoutDayOfWeek.MONDAY).stream()
                .filter(pe -> pe.getExercise().getName().equals("Push-Up"))
                .findFirst().orElseThrow();
        assertThat(updatedPushUp.getSets()).isEqualTo(5);
        assertThat(updatedPushUp.getReps()).isEqualTo(15);
        assertThat(updatedPushUp.getRestSeconds()).isEqualTo(60);

        // 4. Expiration test: expire preview in DB -> confirm returns 409 PREVIEW_EXPIRED
        AiMessageResponse expPreview = sendMessage(athleteToken, convId, "Remove Push-Up from Monday.");
        String expId = extractPreviewId(expPreview);
        AiWorkoutPlanChangePreview entity = previewRepository.findByPreviewIdAndUserId(expId, athlete.getId()).orElseThrow();
        entity.setExpiresAt(ZonedDateTime.now().minusMinutes(1));
        previewRepository.saveAndFlush(entity);

        mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/previews/" + expId + "/confirm")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_EXPIRED"))
                .andExpect(jsonPath("$.mutated").value(false));

        assertThat(dayExerciseNames(WorkoutDayOfWeek.MONDAY)).contains("Push-Up");
    }

    @Test
    @DisplayName("Atomic rollback on mid-batch failure leaves DB completely untouched; read-only queries preserve pending preview")
    void atomicRollbackAndReadOnlyPreservesPendingPreview() throws Exception {
        Long convId = createConversation(athleteToken);

        // 1. Read-only query while a preview is pending must NOT discard the pending preview
        AiMessageResponse movePreview = sendMessage(athleteToken, convId, "Move Front Lever Raises to Tuesday.");
        String movePreviewId = extractPreviewId(movePreview);

        AiMessageResponse readOnlyCheck = sendMessage(athleteToken, convId, "Show Tuesday.");
        assertThat(readOnlyCheck.getIntent()).isEqualTo("WORKOUT_DAY_VIEW");
        assertThat(readOnlyCheck.getMetadata()).contains("\"readOnly\":true");

        // Pending preview is STILL active after read-only query!
        mockMvc.perform(get("/api/v1/ai/conversations/" + convId + "/previews/pending")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(movePreviewId))
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Cancel that one cleanly
        sendMessage(athleteToken, convId, "Cancel.");

        // 2. Atomic rollback: create a 2-op preview where op 1 removes Push-Up and op 2 adds duplicate Front Lever on Tuesday,
        // bypassing planner by mutating stored operations JSON to force failure during op 2 inside atomicMutationExecutor.
        AiMessageResponse removePreview = sendMessage(athleteToken, convId, "Remove Push-Up from Monday.");
        String rollbackPreviewId = extractPreviewId(removePreview);
        AiWorkoutPlanChangePreview stored = previewRepository
                .findByPreviewIdAndUserId(rollbackPreviewId, athlete.getId()).orElseThrow();

        WorkoutPlanDay mon = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(workoutPlan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.MONDAY).findFirst().orElseThrow();
        WorkoutPlanExercise monPushUp = dayExercises(WorkoutDayOfWeek.MONDAY).stream()
                .filter(pe -> pe.getExercise().getName().equals("Push-Up")).findFirst().orElseThrow();

        // Two-operation batch: Op 1 valid REMOVE Push-Up from Monday; Op 2 ADD Push-Up to Monday twice (second fails inside executor!)
        String twoOpJson = """
                [
                  {
                    "operationType": "REMOVE_EXERCISE",
                    "sourceDayId": %d,
                    "sourceDayOfWeek": "MONDAY",
                    "workoutPlanExerciseId": %d,
                    "exerciseId": %d,
                    "exerciseName": "Push-Up"
                  },
                  {
                    "operationType": "ADD_EXERCISE",
                    "destinationDayId": %d,
                    "destinationDayOfWeek": "MONDAY",
                    "exerciseId": %d,
                    "exerciseName": "Push-Up",
                    "proposedValues": {"sets": 3, "reps": 10, "restSeconds": 90}
                  },
                  {
                    "operationType": "ADD_EXERCISE",
                    "destinationDayId": %d,
                    "destinationDayOfWeek": "MONDAY",
                    "exerciseId": %d,
                    "exerciseName": "Push-Up",
                    "proposedValues": {"sets": 3, "reps": 10, "restSeconds": 90}
                  }
                ]
                """.formatted(
                mon.getId(), monPushUp.getId(), pushUp.getId(),
                mon.getId(), pushUp.getId(),
                mon.getId(), pushUp.getId()
        );
        stored.setOperationsJson(twoOpJson);
        previewRepository.saveAndFlush(stored);

        // Confirming triggers op 1 & op 2, then op 3 fails on duplicate inside atomicMutationExecutor -> ENTIRE tx rolls back!
        mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/previews/" + rollbackPreviewId + "/confirm")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.mutated").value(false))
                .andExpect(jsonPath("$.code").value("EXECUTION_FAILED"));

        // Verify Push-Up on Monday is STILL intact with its original ID and prescription (op 1 rolled back!)
        List<WorkoutPlanExercise> afterRollback = dayExercises(WorkoutDayOfWeek.MONDAY);
        assertThat(afterRollback).extracting(pe -> pe.getExercise().getName())
                .containsExactly("Push-Up", "Front Lever Raises");
        assertThat(afterRollback.get(0).getId()).isEqualTo(monPushUp.getId());
        assertThat(afterRollback.get(0).getSets()).isEqualTo(4);
        assertThat(afterRollback.get(0).getReps()).isEqualTo(12);

        // Preview was marked FAILED
        assertThat(previewRepository.findByPreviewIdAndUserId(rollbackPreviewId, athlete.getId()))
                .get().extracting(AiWorkoutPlanChangePreview::getStatus)
                .isEqualTo(WorkoutPlanPreviewStatus.FAILED);
    }
}
