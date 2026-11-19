package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.calisthenics.backend.aichat.dto.CreateAiConversationRequest;
import com.calisthenics.backend.aichat.dto.SendAiMessageRequest;
import com.calisthenics.backend.aichat.dto.preview.CreateRecommendationPreviewRequest;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
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
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class IntelligentRecommendationAndConsultationIntegrationTest {

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
            "exercise_muscle_targets",
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
    @Autowired private ClientProfileRepository clientProfileRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Autowired private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;

    @MockBean private GeminiApiClient geminiApiClient;

    private User athlete;
    private String athleteToken;
    private WorkoutPlan workoutPlan;

    private Exercise pushUp;
    private Exercise tuckPlanche;
    private Exercise plancheLean;
    private Exercise pseudoPlanchePushUp;
    private Exercise frontLever;
    private Exercise pullUp;
    private Exercise pistolSquat;
    private Exercise bulgarianSplitSquat;
    private Exercise romanianDeadlift;
    private Exercise nordicCurl;
    private Exercise calfRaises;

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
                .email("rec_athlete_" + ts + "@test.com")
                .fullName("Rec Athlete")
                .role(Role.ROLE_USER)
                .build());
        athleteToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athlete));

        clientProfileRepository.save(ClientProfile.builder()
                .user(athlete)
                .experienceLevel("INTERMEDIATE")
                .primaryGoals("Straddle Planche and balanced lower body")
                .preferredFrequency("4 days/week")
                .build());

        pushUp = saveExercise("Push-Up", "PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.BEGINNER,
                "Chest, Front Deltoids", "Triceps, Rectus Abdominis", "Floor", 4, "12", 90);
        tuckPlanche = saveExercise("Tuck Planche", "TUCK_PLANCHE", ExerciseCategory.ISOMETRIC, DifficultyLevel.INTERMEDIATE,
                "Front Deltoids", "Chest, Triceps, Rectus Abdominis", "Parallettes", 4, "15s", 120);
        plancheLean = saveExercise("Planche Lean", "PLANCHE_LEAN", ExerciseCategory.SKILL, DifficultyLevel.BEGINNER,
                "Front Deltoids, Chest", "Triceps, Core", "Floor", 3, "20s", 90);
        pseudoPlanchePushUp = saveExercise("Pseudo Planche Push-Up", "PSEUDO_PLANCHE_PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.INTERMEDIATE,
                "Front Deltoids, Chest", "Triceps, Forearms", "Floor", 4, "8", 90);
        frontLever = saveExercise("Front Lever", "FRONT_LEVER", ExerciseCategory.SKILL, DifficultyLevel.ADVANCED,
                "Latissimus Dorsi, Teres Major", "Rhomboids, Rear Deltoids", "Pull-Up Bar", 4, "12s", 120);
        pullUp = saveExercise("Pull-Up", "PULL_UP", ExerciseCategory.PULL, DifficultyLevel.INTERMEDIATE,
                "Latissimus Dorsi", "Biceps, Rhomboids", "Pull-Up Bar", 4, "8", 120);
        pistolSquat = saveExercise("Pistol Squat", "PISTOL_SQUAT", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Quadriceps, Glutes", "Hamstrings, Calves", "Floor", 3, "8", 90);
        bulgarianSplitSquat = saveExercise("Bulgarian Split Squat", "BULGARIAN_SPLIT_SQUAT", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Quadriceps, Glutes", "Hamstrings, Adductors", "Bench or Box", 4, "10", 75);
        romanianDeadlift = saveExercise("Single-Leg Romanian Deadlift", "SINGLE_LEG_RDL", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Hamstrings, Glutes", "Erector Spinae", "Floor or Dumbbell", 4, "10", 75);
        nordicCurl = saveExercise("Nordic Hamstring Curl", "NORDIC_CURL", ExerciseCategory.LEGS, DifficultyLevel.ADVANCED,
                "Hamstrings", "Glutes, Calves", "Floor / Anchor", 3, "6", 120);
        calfRaises = saveExercise("Standing Calf Raises", "STANDING_CALF_RAISES", ExerciseCategory.LEGS, DifficultyLevel.BEGINNER,
                "Calves", "Tibialis", "Step or Floor", 3, "15", 60);

        workoutPlan = buildInitialPlan();
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private Exercise saveExercise(
            String name,
            String code,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            String primaryMuscles,
            String secondaryMuscles,
            String equipment,
            int sets,
            String reps,
            int rest
    ) {
        return exerciseRepository.save(Exercise.builder()
                .name(name)
                .code(code)
                .category(category)
                .difficulty(difficulty)
                .targetMuscles(primaryMuscles)
                .secondaryMuscles(secondaryMuscles)
                .equipment(equipment)
                .recommendedSets(sets)
                .recommendedReps(reps)
                .recommendedRestSeconds(rest)
                .imageUrl("https://cdn.example.com/" + code.toLowerCase() + ".png")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());
    }

    private WorkoutPlan buildInitialPlan() {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athlete)
                .name("Skill & Balance Split")
                .splitType(WorkoutSplitType.UPPER_LOWER)
                .active(true)
                .build();

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean rest = dow == WorkoutDayOfWeek.THURSDAY || dow == WorkoutDayOfWeek.SUNDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(rest)
                    .name(switch (dow) {
                        case MONDAY -> "Push & Planche";
                        case TUESDAY -> "Pull & Levers";
                        case WEDNESDAY -> "Leg Day";
                        case FRIDAY -> "Planche Strength";
                        case SATURDAY -> "Pull Conditioning";
                        default -> "Rest Day";
                    })
                    .sessionType(switch (dow) {
                        case MONDAY, FRIDAY -> WorkoutSessionType.PUSH;
                        case TUESDAY, SATURDAY -> WorkoutSessionType.PULL;
                        case WEDNESDAY -> WorkoutSessionType.LOWER;
                        default -> WorkoutSessionType.REST;
                    })
                    .build();

            if (dow == WorkoutDayOfWeek.MONDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pushUp).sets(4).reps(12).restSeconds(90).displayOrder(1).build());
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(tuckPlanche).sets(5).durationSeconds(15).restSeconds(120).displayOrder(2).build());
            } else if (dow == WorkoutDayOfWeek.TUESDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pullUp).sets(4).reps(8).restSeconds(120).displayOrder(1).build());
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(frontLever).sets(4).durationSeconds(12).restSeconds(120).displayOrder(2).build());
            } else if (dow == WorkoutDayOfWeek.WEDNESDAY) {
                // Only quad-dominant Pistol Squat -> Hamstrings and Calves are undertrained!
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pistolSquat).sets(4).reps(8).restSeconds(90).displayOrder(1).build());
            } else if (dow == WorkoutDayOfWeek.FRIDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(plancheLean).sets(5).durationSeconds(20).restSeconds(90).displayOrder(1).build());
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(pseudoPlanchePushUp).sets(5).reps(8).restSeconds(90).displayOrder(2).build());
            }
            plan.getDays().add(day);
        }
        return workoutPlanRepository.save(plan);
    }

    private Long createConversation() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAiConversationRequest("Planner Chat"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private AiMessageResponse sendMessage(Long convId, String msg) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendAiMessageRequest(msg, "UTC"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(StandardCharsets.UTF_8), AiMessageResponse.class);
    }

    private List<String> dayExerciseNames(WorkoutDayOfWeek dow) {
        WorkoutPlanDay day = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(workoutPlan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == dow)
                .findFirst().orElseThrow();
        return workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId()).stream()
                .map(pe -> pe.getExercise().getName())
                .toList();
    }

    @Test
    @DisplayName("Recommendations: leg day, planche, hamstrings, and missing routine exercises NEVER ask for exercise+weekday upfront")
    void recommendationPromptsReturnStructuredPersonalizedResults() throws Exception {
        Long convId = createConversation();

        // 1. Failing prompt from bug report: "Recommend some leg exercises to add to my leg day."
        AiMessageResponse legRec = sendMessage(convId, "Recommend some leg exercises to add to my leg day.");
        assertThat(legRec.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(legRec.getContent()).doesNotContain("Please specify both the exercise name and the weekday");
        JsonNode legMeta = objectMapper.readTree(legRec.getMetadata());
        assertThat(legMeta.path("type").asText()).isEqualTo("EXERCISE_RECOMMENDATIONS");
        assertThat(legMeta.path("mutated").asBoolean()).isFalse();
        assertThat(legMeta.path("suggestedDayOfWeek").asText()).isEqualTo("WEDNESDAY");
        assertThat(legMeta.path("undertrainedMuscles").toString()).contains("Hamstrings");
        List<String> recommendedLegNames = legMeta.path("recommendations").findValuesAsText("name");
        assertThat(recommendedLegNames).isNotEmpty();
        assertThat(recommendedLegNames).doesNotContain("Pistol Squat"); // Already on Wednesday
        assertThat(recommendedLegNames).contains("Single-Leg Romanian Deadlift");

        // 2. "What exercises should I add to my leg day?"
        AiMessageResponse whatAdd = sendMessage(convId, "What exercises should I add to my leg day?");
        assertThat(whatAdd.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(whatAdd.getMetadata()).contains("EXERCISE_RECOMMENDATIONS");

        // 3. "Suggest exercises to improve my planche."
        AiMessageResponse plancheRec = sendMessage(convId, "Suggest exercises to improve my planche.");
        assertThat(plancheRec.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        JsonNode plancheMeta = objectMapper.readTree(plancheRec.getMetadata());
        assertThat(plancheMeta.path("recommendations").toString()).containsAnyOf("Tuck Planche", "Planche Lean", "Pseudo Planche Push-Up");

        // 4. "Give me some hamstring exercises."
        AiMessageResponse hamRec = sendMessage(convId, "Give me some hamstring exercises.");
        assertThat(hamRec.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        JsonNode hamMeta = objectMapper.readTree(hamRec.getMetadata());
        assertThat(hamMeta.path("recommendations").findValuesAsText("name"))
                .containsAnyOf("Single-Leg Romanian Deadlift", "Nordic Hamstring Curl");

        // 5. "What exercises am I missing in my current routine?"
        AiMessageResponse missingRec = sendMessage(convId, "What exercises am I missing in my current routine?");
        assertThat(missingRec.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(missingRec.getMetadata()).contains("EXERCISE_RECOMMENDATIONS");

        // Zero DB mutations happened for all recommendation queries
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY)).containsExactly("Pistol Squat");
    }

    @Test
    @DisplayName("Consultation: balance, hamstrings volume, recovery, and planche volume analyze real schedule without mutating DB")
    void consultationPromptsAnalyzeScheduleWithoutMutatingDatabase() throws Exception {
        Long convId = createConversation();

        AiMessageResponse legBalance = sendMessage(convId, "Is my leg day balanced?");
        assertThat(legBalance.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        JsonNode legCon = objectMapper.readTree(legBalance.getMetadata());
        assertThat(legCon.path("type").asText()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(legCon.path("mutated").asBoolean()).isFalse();
        assertThat(legCon.path("undertrainedMuscles").toString()).contains("Hamstrings");

        AiMessageResponse hamCheck = sendMessage(convId, "Am I training my hamstrings enough?");
        assertThat(hamCheck.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(hamCheck.getMetadata()).contains("Hamstrings");

        AiMessageResponse recoveryCheck = sendMessage(convId, "Does my weekly routine have enough recovery?");
        assertThat(recoveryCheck.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(recoveryCheck.getContent()).contains("Rest Day");

        AiMessageResponse plancheVolCheck = sendMessage(convId, "Is my current planche training volume too high?");
        assertThat(plancheVolCheck.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(plancheVolCheck.getContent()).contains("Front Deltoid");

        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY)).containsExactly("Pistol Squat");
    }

    @Test
    @DisplayName("Multi-turn selection: recommend -> pick favorite exercises -> add them to Wednesday -> Preview -> Confirm")
    void multiTurnRecommendationSelectionToPreviewAndConfirm() throws Exception {
        Long convId = createConversation();

        // Turn 1: Ask for leg exercises
        AiMessageResponse recs = sendMessage(convId, "Recommend some leg exercises.");
        assertThat(recs.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");

        // Turn 2: Pick two recommended exercises without repeating weekday
        AiMessageResponse pick = sendMessage(convId, "I like Bulgarian Split Squat and Single-Leg Romanian Deadlift.");
        assertThat(pick.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(pick.getMetadata()).contains("WORKOUT_PLAN_CLARIFICATION");
        assertThat(pick.getMetadata()).contains("ADD_MULTI");
        assertThat(pick.getContent()).contains("Bulgarian Split Squat");
        assertThat(pick.getContent()).contains("Single-Leg Romanian Deadlift");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY)).containsExactly("Pistol Squat");

        // Turn 3: Specify Wednesday -> generates single batch preview for both exercises (ZERO DB mutation)
        AiMessageResponse preview = sendMessage(convId, "Add them to Wednesday.");
        assertThat(preview.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(preview.getContent()).contains("Bulgarian Split Squat");
        assertThat(preview.getContent()).contains("Single-Leg Romanian Deadlift");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY)).containsExactly("Pistol Squat");

        // Turn 4: Explicit confirm -> atomically persists both exercises
        AiMessageResponse confirmed = sendMessage(convId, "Confirm.");
        assertThat(confirmed.getMetadata()).contains("\"mutated\":true");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY))
                .containsExactly("Pistol Squat", "Bulgarian Split Squat", "Single-Leg Romanian Deadlift");
    }

    @Test
    @DisplayName("Interactive UI selection endpoint previews batch add with duplicate skipping and explicit modification remains intact")
    void recommendationPreviewEndpointAndModificationRegressionSafety() throws Exception {
        Long convId = createConversation();

        // 1. UI selects Pistol Squat (already on Wednesday!) + Bulgarian Split Squat -> skips duplicate cleanly with warning
        CreateRecommendationPreviewRequest req = CreateRecommendationPreviewRequest.builder()
                .exerciseIds(List.of(pistolSquat.getId(), bulgarianSplitSquat.getId()))
                .dayOfWeek(WorkoutDayOfWeek.WEDNESDAY)
                .build();

        MvcResult previewRes = mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/recommendations/preview")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        AiMessageResponse previewMsg = objectMapper.readValue(
                previewRes.getResponse().getContentAsString(StandardCharsets.UTF_8), AiMessageResponse.class);
        assertThat(previewMsg.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(previewMsg.getContent()).contains("Bulgarian Split Squat");
        assertThat(previewMsg.getContent()).contains("already scheduled on Wednesday (skipped duplicate)");

        // Confirm adds ONLY non-duplicate Bulgarian Split Squat
        sendMessage(convId, "Confirm.");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY))
                .containsExactly("Pistol Squat", "Bulgarian Split Squat");

        // 2. Existing explicit WORKOUT_MODIFICATION still generates preview & adds single named exercise
        AiMessageResponse explicitAdd = sendMessage(convId, "Add Standing Calf Raises to Wednesday.");
        assertThat(explicitAdd.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(explicitAdd.getContent()).contains("Standing Calf Raises");
        sendMessage(convId, "Confirm.");
        assertThat(dayExerciseNames(WorkoutDayOfWeek.WEDNESDAY))
                .containsExactly("Pistol Squat", "Bulgarian Split Squat", "Standing Calf Raises");
    }
}
