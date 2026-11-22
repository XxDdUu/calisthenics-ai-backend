package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.calisthenics.backend.aichat.dto.CreateAiConversationRequest;
import com.calisthenics.backend.aichat.dto.GeminiAssistantOutput;
import com.calisthenics.backend.aichat.dto.SendAiMessageRequest;
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
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPlanContextRetrievalIntegrationTest {

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
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Autowired private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;

    @MockBean private GeminiApiClient geminiApiClient;

    private User athlete;
    private String athleteToken;
    private WorkoutPlan activePlan;

    private Exercise pushUp;
    private Exercise tuckPlanche;
    private Exercise straddlePlanche;
    private Exercise hspu;
    private Exercise pullUp;
    private Exercise frontLever;
    private Exercise tuckFrontLever;
    private Exercise frontLeverRaises;
    private Exercise pistolSquat;

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
                .email("context_athlete_" + ts + "@test.com")
                .fullName("Context Athlete")
                .role(Role.ROLE_USER)
                .build());
        athleteToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athlete));

        pushUp = saveExercise("Push-Up", "PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.BEGINNER,
                "Chest, Front Deltoids", "Triceps");
        tuckPlanche = saveExercise("Tuck Planche", "TUCK_PLANCHE", ExerciseCategory.ISOMETRIC, DifficultyLevel.INTERMEDIATE,
                "Front Deltoids", "Chest, Triceps");
        straddlePlanche = saveExercise("Straddle Planche", "STRADDLE_PLANCHE", ExerciseCategory.SKILL, DifficultyLevel.ADVANCED,
                "Front Deltoids", "Chest, Triceps");
        hspu = saveExercise("Handstand Push-Up", "HANDSTAND_PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.ADVANCED,
                "Front Deltoids, Triceps", "Upper Trapezius");
        pullUp = saveExercise("Pull-Up", "PULL_UP", ExerciseCategory.PULL, DifficultyLevel.INTERMEDIATE,
                "Latissimus Dorsi", "Biceps, Rhomboids");
        frontLever = saveExercise("Front Lever", "FRONT_LEVER", ExerciseCategory.SKILL, DifficultyLevel.ADVANCED,
                "Latissimus Dorsi, Teres Major", "Rhomboids, Rear Deltoids");
        tuckFrontLever = saveExercise("Tuck Front Lever", "TUCK_FRONT_LEVER", ExerciseCategory.ISOMETRIC, DifficultyLevel.INTERMEDIATE,
                "Latissimus Dorsi, Teres Major", "Rhomboids, Rectus Abdominis");
        frontLeverRaises = saveExercise("Front Lever Raises", "FRONT_LEVER_RAISES", ExerciseCategory.PULL, DifficultyLevel.INTERMEDIATE,
                "Latissimus Dorsi, Teres Major", "Rhomboids, Rectus Abdominis");
        pistolSquat = saveExercise("Pistol Squat", "PISTOL_SQUAT", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Quadriceps, Glutes", "Hamstrings, Calves");

        activePlan = buildExampleSevenDayPlan();
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
            String secondaryMuscles
    ) {
        return exerciseRepository.save(Exercise.builder()
                .name(name)
                .code(code)
                .category(category)
                .difficulty(difficulty)
                .targetMuscles(primaryMuscles)
                .secondaryMuscles(secondaryMuscles)
                .recommendedSets(3)
                .recommendedReps(category == ExerciseCategory.SKILL || category == ExerciseCategory.ISOMETRIC ? "15s" : "10")
                .recommendedRestSeconds(90)
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());
    }

    /**
     * Builds the exact 7-day plan from the problem statement:
     * - Monday: Planche + Push
     * - Tuesday: Front Lever + Pull
     * - Wednesday: Legs
     * - Thursday: Planche + Front Lever (Light)
     * - Friday: HSPU + Pull
     * - Saturday: Planche + Front Lever (Hard)
     * - Sunday: Rest Day
     */
    private WorkoutPlan buildExampleSevenDayPlan() {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athlete)
                .name("Skill & Strength Split")
                .splitType(WorkoutSplitType.CUSTOM)
                .active(true)
                .build();

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean isRest = dow == WorkoutDayOfWeek.SUNDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(isRest)
                    .name(switch (dow) {
                        case MONDAY -> "Planche + Push";
                        case TUESDAY -> "Front Lever + Pull";
                        case WEDNESDAY -> "Legs";
                        case THURSDAY -> "Planche + Front Lever (Light)";
                        case FRIDAY -> "HSPU + Pull";
                        case SATURDAY -> "Planche + Front Lever (Hard)";
                        case SUNDAY -> "Rest Day";
                    })
                    .sessionType(switch (dow) {
                        case MONDAY -> WorkoutSessionType.PUSH;
                        case TUESDAY, FRIDAY -> WorkoutSessionType.PULL;
                        case WEDNESDAY -> WorkoutSessionType.LEGS;
                        case THURSDAY, SATURDAY -> WorkoutSessionType.SKILL_TRAINING;
                        case SUNDAY -> WorkoutSessionType.REST;
                    })
                    .build();

            switch (dow) {
                case MONDAY -> {
                    addEx(day, tuckPlanche, 4, null, 15, 1);
                    addEx(day, pushUp, 4, 12, null, 2);
                }
                case TUESDAY -> {
                    addEx(day, frontLever, 4, null, 12, 1);
                    addEx(day, pullUp, 4, 8, null, 2);
                }
                case WEDNESDAY -> addEx(day, pistolSquat, 4, 8, null, 1);
                case THURSDAY -> {
                    addEx(day, tuckPlanche, 3, null, 12, 1);
                    addEx(day, tuckFrontLever, 3, null, 12, 2);
                }
                case FRIDAY -> {
                    addEx(day, hspu, 4, 6, null, 1);
                    addEx(day, pullUp, 4, 10, null, 2);
                }
                case SATURDAY -> {
                    addEx(day, straddlePlanche, 5, null, 10, 1);
                    addEx(day, frontLever, 5, null, 12, 2);
                    addEx(day, pushUp, 3, 15, null, 3);
                    addEx(day, pullUp, 3, 10, null, 4);
                }
                case SUNDAY -> {}
            }
            plan.getDays().add(day);
        }
        return workoutPlanRepository.save(plan);
    }

    private void addEx(WorkoutPlanDay day, Exercise ex, int sets, Integer reps, Integer durSec, int order) {
        day.getExercises().add(WorkoutPlanExercise.builder()
                .workoutPlanDay(day)
                .exercise(ex)
                .sets(sets)
                .reps(reps)
                .durationSeconds(durSec)
                .restSeconds(90)
                .displayOrder(order)
                .build());
    }

    private Long createConversation() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAiConversationRequest("Planner Context"))))
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

    private List<String> dayExerciseNames(Long planId, WorkoutDayOfWeek dow) {
        WorkoutPlanDay day = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(planId).stream()
                .filter(d -> d.getDayOfWeek() == dow)
                .findFirst().orElseThrow();
        return workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId()).stream()
                .map(pe -> pe.getExercise().getName())
                .toList();
    }

    @Test
    @DisplayName("Scenarios 1 & 2 & End-to-End: Proactive plan check -> 'My pull day' lists Tuesday & Friday -> Tuesday preview avoids duplicates -> Confirm updates DB")
    void scenarios1And2_proactiveRecommendation_multiPullDayDisambiguation_andConfirmWorkflow() throws Exception {
        Long convId = createConversation();

        // Scenario 1: Turn 1 - "Add some Front Lever exercises to my routine."
        AiMessageResponse turn1 = sendMessage(convId, "Add some Front Lever exercises to my routine.");
        assertThat(turn1.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        JsonNode meta1 = objectMapper.readTree(turn1.getMetadata());
        assertThat(meta1.path("type").asText()).isEqualTo("WORKOUT_PLAN_CLARIFICATION");
        assertThat(meta1.path("mutated").asBoolean()).isFalse();

        // Must inspect actual active plan, recommend best suitable day (Tuesday), offer alternative(s), and NEVER recommend Sunday (Rest Day)
        assertThat(turn1.getContent()).contains("Skill & Strength Split");
        assertThat(turn1.getContent()).contains("Tuesday");
        assertThat(turn1.getContent()).doesNotContain("Sunday");
        assertThat(meta1.path("recommendedDayOfWeek").asText()).isEqualTo("TUESDAY");

        // Scenario 2: Turn 2 - User answers "My pull day."
        // Even if Gemini were online, our deterministic context router identifies BOTH Tuesday & Friday (never Sunday!)
        AiMessageResponse turn2 = sendMessage(convId, "My pull day.");
        assertThat(turn2.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        JsonNode meta2 = objectMapper.readTree(turn2.getMetadata());
        assertThat(meta2.path("type").asText()).isEqualTo("WORKOUT_PLAN_CLARIFICATION");
        assertThat(meta2.path("mutated").asBoolean()).isFalse();
        assertThat(turn2.getContent()).contains("Tuesday");
        assertThat(turn2.getContent()).contains("Friday");
        assertThat(turn2.getContent()).doesNotContain("Sunday");
        assertThat(meta2.path("candidateDays").toString()).contains("TUESDAY", "FRIDAY");
        assertThat(meta2.path("candidateDays").toString()).doesNotContain("SUNDAY");

        // Turn 3 - User picks "Tuesday" -> retrieves Tuesday's existing exercises (Front Lever, Pull-Up),
        // avoids duplicate Front Lever, proposes Front Lever Raises in preview without mutating DB yet
        AiMessageResponse turn3 = sendMessage(convId, "Tuesday");
        assertThat(turn3.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        JsonNode meta3 = objectMapper.readTree(turn3.getMetadata());
        assertThat(meta3.path("mutated").asBoolean()).isFalse();
        assertThat(turn3.getContent()).contains("Tuesday");
        assertThat(turn3.getContent()).contains("Front Lever Raises");
        // Front Lever is already on Tuesday -> must NOT be added again as duplicate!
        assertThat(turn3.getContent()).doesNotContain("ADD: Front Lever —");
        // Verify ZERO DB mutation before confirmation
        assertThat(dayExerciseNames(activePlan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Pull-Up");

        // Turn 4 - User confirms -> atomically applies to Tuesday, preserves existing exercises
        AiMessageResponse turn4 = sendMessage(convId, "Confirm.");
        assertThat(turn4.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(turn4.getMetadata()).contains("\"mutated\":true");
        assertThat(dayExerciseNames(activePlan.getId(), WorkoutDayOfWeek.TUESDAY))
                .contains("Front Lever", "Pull-Up", "Front Lever Raises", "Tuck Front Lever");
        // Sunday remains untouched as Rest Day
        WorkoutPlanDay sunday = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(activePlan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.SUNDAY).findFirst().orElseThrow();
        assertThat(sunday.isRestDay()).isTrue();
    }

    @Test
    @DisplayName("Scenario 3: 'My Front Lever day' identifies all matching days from focus and assigned exercises (Tuesday, Thursday, Saturday)")
    void scenario3_myFrontLeverDay_identifiesAllMatchingDays() throws Exception {
        Long convId = createConversation();

        sendMessage(convId, "Add some Front Lever exercises to my routine.");
        AiMessageResponse flDaysResp = sendMessage(convId, "My Front Lever day.");

        assertThat(flDaysResp.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        JsonNode meta = objectMapper.readTree(flDaysResp.getMetadata());
        assertThat(meta.path("type").asText()).isEqualTo("WORKOUT_PLAN_CLARIFICATION");
        assertThat(meta.path("mutated").asBoolean()).isFalse();
        assertThat(flDaysResp.getContent()).contains("Tuesday", "Thursday", "Saturday");
        assertThat(flDaysResp.getContent()).doesNotContain("Sunday");
        assertThat(meta.path("candidateDays").toString()).contains("TUESDAY", "THURSDAY", "SATURDAY");
    }

    @Test
    @DisplayName("Scenario 4: 'Sunday.' recognizes Sunday as Rest Day, warns clearly, never treats as Pull Day, and preserves DB before confirmation")
    void scenario4_sunday_recognizedAsRestDayAndNotSilentlyTreatedAsPullDay() throws Exception {
        Long convId = createConversation();

        sendMessage(convId, "Add some Front Lever exercises to my routine.");
        AiMessageResponse sundayResp = sendMessage(convId, "Sunday.");

        assertThat(sundayResp.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(sundayResp.getMetadata()).contains("\"mutated\":false");
        assertThat(sundayResp.getContent()).contains("Sunday");
        assertThat(sundayResp.getContent()).contains("Rest Day");
        assertThat(sundayResp.getContent()).doesNotContain("Sunday — PULL");

        // Sunday is still a Rest Day in DB because user has not confirmed!
        WorkoutPlanDay sunday = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(activePlan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.SUNDAY).findFirst().orElseThrow();
        assertThat(sunday.isRestDay()).isTrue();
        assertThat(dayExerciseNames(activePlan.getId(), WorkoutDayOfWeek.SUNDAY)).isEmpty();

        // User can switch away from Rest Day ("Actually Tuesday") without touching Sunday
        AiMessageResponse switchedToTue = sendMessage(convId, "Actually Tuesday.");
        assertThat(switchedToTue.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(switchedToTue.getContent()).contains("Tuesday");
        sendMessage(convId, "Confirm.");

        WorkoutPlanDay sundayAfter = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(activePlan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.SUNDAY).findFirst().orElseThrow();
        assertThat(sundayAfter.isRestDay()).isTrue();
        assertThat(dayExerciseNames(activePlan.getId(), WorkoutDayOfWeek.TUESDAY))
                .contains("Front Lever", "Pull-Up", "Front Lever Raises", "Tuck Front Lever");
    }

    @Test
    @DisplayName("Scenario 5: Requesting a day focus that does not exist in current plan explains the gap instead of inventing a weekday")
    void scenario5_nonExistentDayFocus_explainsInsteadOfInventingWeekday() throws Exception {
        // Replace active plan with an upper-body-only plan (NO leg day at all!)
        activePlan.setActive(false);
        workoutPlanRepository.save(activePlan);

        WorkoutPlan upperOnly = WorkoutPlan.builder()
                .user(athlete)
                .name("Upper Body Only")
                .splitType(WorkoutSplitType.UPPER_LOWER)
                .active(true)
                .build();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean isTraining = dow == WorkoutDayOfWeek.MONDAY || dow == WorkoutDayOfWeek.THURSDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(upperOnly)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(!isTraining)
                    .name(switch (dow) {
                        case MONDAY -> "Push Strength";
                        case THURSDAY -> "Pull Strength";
                        default -> "Rest Day";
                    })
                    .sessionType(switch (dow) {
                        case MONDAY -> WorkoutSessionType.PUSH;
                        case THURSDAY -> WorkoutSessionType.PULL;
                        default -> WorkoutSessionType.REST;
                    })
                    .build();
            if (dow == WorkoutDayOfWeek.MONDAY) addEx(day, pushUp, 4, 12, null, 1);
            if (dow == WorkoutDayOfWeek.THURSDAY) addEx(day, pullUp, 4, 8, null, 1);
            upperOnly.getDays().add(day);
        }
        workoutPlanRepository.save(upperOnly);

        Long convId = createConversation();
        AiMessageResponse noLegDay = sendMessage(convId, "Add Pistol Squat to my leg day.");

        assertThat(noLegDay.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        JsonNode meta = objectMapper.readTree(noLegDay.getMetadata());
        assertThat(meta.path("type").asText()).isEqualTo("WORKOUT_PLAN_CLARIFICATION");
        assertThat(meta.path("mutated").asBoolean()).isFalse();
        assertThat(noLegDay.getContent()).containsIgnoringCase("doesn't currently contain a matching **leg** day");
        assertThat(noLegDay.getContent()).contains("Monday", "Thursday");

        // Verify NO day was modified or invented
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            assertThat(dayExerciseNames(upperOnly.getId(), dow)).doesNotContain("Pistol Squat");
        }
    }

    @Test
    @DisplayName("Dynamic schedule (Pull on Wednesday, Rest on Monday/Saturday) + single pull day proposal + structured Gemini system prompt")
    void dynamicScheduleAndSystemPromptContextVerification() throws Exception {
        activePlan.setActive(false);
        workoutPlanRepository.save(activePlan);

        // Create a completely different schedule where Pull Day is ONLY on Wednesday and Monday/Saturday are Rest Days
        WorkoutPlan shiftedPlan = WorkoutPlan.builder()
                .user(athlete)
                .name("Midweek Pull Split")
                .splitType(WorkoutSplitType.CUSTOM)
                .active(true)
                .build();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean isRest = dow == WorkoutDayOfWeek.MONDAY || dow == WorkoutDayOfWeek.SATURDAY || dow == WorkoutDayOfWeek.SUNDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(shiftedPlan)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(isRest)
                    .name(switch (dow) {
                        case TUESDAY -> "Push Strength";
                        case WEDNESDAY -> "Pull Focus";
                        case THURSDAY -> "Legs & Core";
                        case FRIDAY -> "Handstand Balance";
                        default -> "Rest Day";
                    })
                    .sessionType(switch (dow) {
                        case TUESDAY -> WorkoutSessionType.PUSH;
                        case WEDNESDAY -> WorkoutSessionType.PULL;
                        case THURSDAY -> WorkoutSessionType.LEGS;
                        case FRIDAY -> WorkoutSessionType.HANDSTAND;
                        default -> WorkoutSessionType.REST;
                    })
                    .build();
            if (dow == WorkoutDayOfWeek.TUESDAY) addEx(day, pushUp, 4, 12, null, 1);
            if (dow == WorkoutDayOfWeek.WEDNESDAY) addEx(day, pullUp, 4, 8, null, 1);
            if (dow == WorkoutDayOfWeek.THURSDAY) addEx(day, pistolSquat, 3, 8, null, 1);
            if (dow == WorkoutDayOfWeek.FRIDAY) addEx(day, hspu, 3, 6, null, 1);
            shiftedPlan.getDays().add(day);
        }
        workoutPlanRepository.save(shiftedPlan);

        Long convId = createConversation();

        // 1. General message verifies Gemini receives structured 7-day schedule in system prompt
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.of(
                GeminiAssistantOutput.builder().intent("GENERAL_CHAT").reply("Keep shoulders depressed.").build()
        ));
        sendMessage(convId, "Any form cues for Front Lever?");
        ArgumentCaptor<String> sysPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(geminiApiClient).callAssistant(sysPromptCaptor.capture(), anyList());
        String capturedPrompt = sysPromptCaptor.getValue();
        assertThat(capturedPrompt).contains("Midweek Pull Split");
        assertThat(capturedPrompt).contains("WEDNESDAY: [TRAINING] Focus=\"Pull Focus\"");
        assertThat(capturedPrompt).contains("Pull-Up [PULL; Primary=Latissimus Dorsi");
        assertThat(capturedPrompt).contains("MONDAY: [REST DAY]");
        assertThat(capturedPrompt).contains("SUNDAY: [REST DAY]");

        // 2. Single Pull Day (Wednesday) -> Turn 1 recommends Wednesday dynamically (never Monday/Saturday/Sunday)
        AiMessageResponse askResp = sendMessage(convId, "Add some Front Lever exercises to my routine.");
        JsonNode askMeta = objectMapper.readTree(askResp.getMetadata());
        assertThat(askMeta.path("recommendedDayOfWeek").asText()).isEqualTo("WEDNESDAY");
        assertThat(askResp.getContent()).contains("Wednesday");
        assertThat(askResp.getContent()).doesNotContain("Monday", "Saturday", "Sunday");

        // 3. Turn 2 "My pull day." uniquely identifies Wednesday and proposes preview for confirmation (mutated: false)
        AiMessageResponse pullResp = sendMessage(convId, "My pull day.");
        assertThat(pullResp.getIntent()).isEqualTo("WORKOUT_PLAN_PREVIEW");
        assertThat(pullResp.getMetadata()).contains("\"mutated\":false");
        assertThat(pullResp.getContent()).contains("Wednesday");
        assertThat(pullResp.getContent()).contains("Front Lever");
        assertThat(dayExerciseNames(shiftedPlan.getId(), WorkoutDayOfWeek.WEDNESDAY)).containsExactly("Pull-Up");

        // Confirm applies cleanly to Wednesday
        sendMessage(convId, "Confirm.");
        assertThat(dayExerciseNames(shiftedPlan.getId(), WorkoutDayOfWeek.WEDNESDAY))
                .contains("Pull-Up", "Front Lever Raises");
    }
}
