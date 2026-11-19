package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.calisthenics.backend.aichat.dto.CreateAiConversationRequest;
import com.calisthenics.backend.aichat.dto.GeminiAssistantOutput;
import com.calisthenics.backend.aichat.dto.SendAiMessageRequest;
import com.calisthenics.backend.aichat.service.UserAiWorkoutToolService;
import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserAiWorkoutReadOnlyAndWriteTest {

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
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Autowired private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    @Autowired private CoachClientRelationshipRepository relationshipRepository;
    @Autowired private WorkoutPlanAssignmentRepository assignmentRepository;

    @MockBean private GeminiApiClient geminiApiClient;
    @SpyBean private AiWorkoutPlanService aiWorkoutPlanService;
    @SpyBean private UserAiWorkoutToolService userAiWorkoutToolService;

    private User athleteWithPlan;
    private User athleteWithoutPlan;
    private User coachUser;
    private String tokenWithPlan;
    private String tokenWithoutPlan;

    private Exercise pushUp;
    private Exercise tuckPlanche;
    private Exercise pullUp;
    private Exercise pistolSquat;
    private Exercise bulgarianSplitSquat;
    private WorkoutPlan personalPlan;

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();

        long ts = System.currentTimeMillis();
        athleteWithPlan = userRepository.save(User.builder()
                .email("plan_user_" + ts + "@test.com")
                .fullName("Alex Athlete")
                .role(Role.ROLE_USER)
                .build());

        athleteWithoutPlan = userRepository.save(User.builder()
                .email("noplan_user_" + ts + "@test.com")
                .fullName("Newcomer Sam")
                .role(Role.ROLE_USER)
                .build());

        coachUser = userRepository.save(User.builder()
                .email("coach_plan_" + ts + "@test.com")
                .fullName("Coach Taylor")
                .role(Role.ROLE_USER)
                .build());

        tokenWithPlan = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athleteWithPlan));
        tokenWithoutPlan = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athleteWithoutPlan));

        pushUp = exerciseRepository.save(Exercise.builder()
                .name("Push-Up")
                .code("PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .description("Horizontal push")
                .instructions("Keep core tight, lower chest cleanly.")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());

        tuckPlanche = exerciseRepository.save(Exercise.builder()
                .name("Tuck Planche")
                .code("TUCK_PLANCHE")
                .category(ExerciseCategory.ISOMETRIC)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .description("Static planche hold")
                .instructions("Protract scapula and lock elbows.")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());

        pullUp = exerciseRepository.save(Exercise.builder()
                .name("Pull-Up")
                .code("PULL_UP")
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .description("Vertical pull")
                .instructions("Pull chest toward bar without kipping.")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());

        pistolSquat = exerciseRepository.save(Exercise.builder()
                .name("Pistol Squat")
                .code("PISTOL_SQUAT")
                .category(ExerciseCategory.LEGS)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .description("Single leg squat")
                .instructions("Descend with control, keep opposite leg straight.")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());

        bulgarianSplitSquat = exerciseRepository.save(Exercise.builder()
                .name("Bulgarian Split Squat")
                .code("BULGARIAN_SPLIT_SQUAT")
                .category(ExerciseCategory.LEGS)
                .difficulty(DifficultyLevel.BEGINNER)
                .description("Rear-foot elevated split squat")
                .instructions("Drop back knee smoothly toward floor.")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build());

        personalPlan = buildWeeklyPlanFor(athleteWithPlan, "Upper Lower Mastery");
        personalPlan = workoutPlanRepository.save(personalPlan);

        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private WorkoutPlan buildWeeklyPlanFor(User owner, String planName) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(owner)
                .name(planName)
                .description("Balanced 4-day calisthenics strength and isometric skill program.")
                .splitType(WorkoutSplitType.UPPER_LOWER)
                .active(true)
                .build();

        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean isTraining = dow == WorkoutDayOfWeek.MONDAY
                    || dow == WorkoutDayOfWeek.WEDNESDAY
                    || dow == WorkoutDayOfWeek.FRIDAY
                    || dow == WorkoutDayOfWeek.SATURDAY;

            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(!isTraining)
                    .name(switch (dow) {
                        case MONDAY -> "Upper Body Strength";
                        case WEDNESDAY -> "Lower Body & Isometric Core";
                        case FRIDAY -> "Push & Skill Endurance";
                        case SATURDAY -> "Pull & Conditioning";
                        default -> "Rest Day";
                    })
                    .sessionType(switch (dow) {
                        case MONDAY -> WorkoutSessionType.UPPER;
                        case WEDNESDAY -> WorkoutSessionType.LOWER;
                        case FRIDAY -> WorkoutSessionType.PUSH;
                        case SATURDAY -> WorkoutSessionType.PULL;
                        default -> WorkoutSessionType.REST;
                    })
                    .build();

            if (isTraining) {
                if (dow == WorkoutDayOfWeek.MONDAY || dow == WorkoutDayOfWeek.FRIDAY) {
                    day.getExercises().add(WorkoutPlanExercise.builder()
                            .workoutPlanDay(day).exercise(pushUp)
                            .sets(4).reps(12).restSeconds(90).displayOrder(1)
                            .notes("2s controlled eccentric").build());
                    day.getExercises().add(WorkoutPlanExercise.builder()
                            .workoutPlanDay(day).exercise(tuckPlanche)
                            .sets(4).durationSeconds(15).restSeconds(120).displayOrder(2)
                            .notes("Focus on full protraction").build());
                } else {
                    day.getExercises().add(WorkoutPlanExercise.builder()
                            .workoutPlanDay(day).exercise(pullUp)
                            .sets(4).reps(8).restSeconds(120).displayOrder(1)
                            .notes("Chest to bar").build());
                }
            }
            plan.getDays().add(day);
        }
        return plan;
    }

    private Long createConv(String token) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAiConversationRequest("Plan Review"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();
    }

    private AiMessageResponse sendMsg(String token, Long convId, String msg) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendAiMessageRequest(msg, "UTC"))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), AiMessageResponse.class);
    }

    @Test
    @DisplayName("Criteria 1-5 & 10: Read-only queries retrieve plans/days/follow-ups in English & Vietnamese without mutating DB")
    void readOnlyQueriesRetrieveWithoutMutatingDatabase() throws Exception {
        long initialPlanCount = workoutPlanRepository.count();
        Long convId = createConv(tokenWithPlan);

        // Even if Gemini mistakenly tries to output WORKOUT_PLAN create, guardrail must keep read-only
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.of(
                GeminiAssistantOutput.builder().intent("WORKOUT_PLAN").reply("Creating plan...").build()
        ));

        // 1. "Show my workout plan"
        AiMessageResponse showPlan = sendMsg(tokenWithPlan, convId, "Show my workout plan.");
        assertThat(showPlan.getIntent()).isEqualTo("WORKOUT_PLAN_VIEW");
        assertThat(showPlan.getContent()).contains("Upper Lower Mastery");
        assertThat(showPlan.getContent()).contains("Push-Up");
        assertThat(showPlan.getContent()).contains("Tuck Planche");
        assertThat(showPlan.getContent()).contains("15s hold");
        assertThat(showPlan.getContent()).contains("Rest Day");
        assertThat(showPlan.getMetadata()).contains("\"readOnly\":true");
        verify(userAiWorkoutToolService).getMyWorkoutPlan(athleteWithPlan.getId(), false, false);

        // 2. "Show my weekly schedule in detail" -> all 7 days + instructions
        AiMessageResponse weeklyDetailed = sendMsg(tokenWithPlan, convId, "Show my weekly schedule in detail.");
        assertThat(weeklyDetailed.getIntent()).isEqualTo("WORKOUT_PLAN_VIEW");
        for (DayOfWeek dow : DayOfWeek.values()) {
            String dayTitle = dow.name().charAt(0) + dow.name().substring(1).toLowerCase();
            assertThat(weeklyDetailed.getContent()).contains(dayTitle);
        }
        assertThat(weeklyDetailed.getContent()).contains("Keep core tight, lower chest cleanly.");

        // 3. "What am I training today?"
        AiMessageResponse todayResp = sendMsg(tokenWithPlan, convId, "What am I training today?");
        assertThat(todayResp.getIntent()).isEqualTo("WORKOUT_DAY_VIEW");
        assertThat(todayResp.getContent()).contains("Today's Workout");

        // 4. "Show Wednesday"
        AiMessageResponse wedResp = sendMsg(tokenWithPlan, convId, "Show Wednesday.");
        assertThat(wedResp.getIntent()).isEqualTo("WORKOUT_DAY_VIEW");
        assertThat(wedResp.getContent()).contains("Wednesday");
        assertThat(wedResp.getContent()).contains("Lower Body & Isometric Core");
        assertThat(wedResp.getContent()).contains("Pull-Up");
        assertThat(wedResp.getContent()).doesNotContain("Upper Body Strength");

        // 5. Follow-up "Show it in more detail" resolves Wednesday from conversation context
        AiMessageResponse followUpWed = sendMsg(tokenWithPlan, convId, "Show it in more detail.");
        assertThat(followUpWed.getIntent()).isEqualTo("WORKOUT_DAY_VIEW");
        assertThat(followUpWed.getContent()).contains("Wednesday");
        assertThat(followUpWed.getContent()).contains("Pull chest toward bar without kipping.");

        // Vietnamese queries
        AiMessageResponse vnPlan = sendMsg(tokenWithPlan, convId, "Lịch tập của tôi thế nào?");
        assertThat(vnPlan.getIntent()).isEqualTo("WORKOUT_PLAN_VIEW");
        assertThat(vnPlan.getContent()).contains("Upper Lower Mastery");

        AiMessageResponse vnToday = sendMsg(tokenWithPlan, convId, "Hôm nay tôi tập gì?");
        assertThat(vnToday.getIntent()).isEqualTo("WORKOUT_DAY_VIEW");

        // Verify ZERO plan generation or DB mutations occurred
        verify(aiWorkoutPlanService, never()).generatePlan(any(), any(), any());
        verify(aiWorkoutPlanService, never()).generatePlan(any(), any());
        assertThat(workoutPlanRepository.count()).isEqualTo(initialPlanCount);
    }

    @Test
    @DisplayName("Criteria 6-7: Explicit create, replace, and modification intents invoke WRITE tools")
    void explicitWriteIntentsInvokeWriteTools() throws Exception {
        Long convId = createConv(tokenWithPlan);

        AiWorkoutPlanPreviewResponse mockPreview = AiWorkoutPlanPreviewResponse.builder()
                .planName("Fresh Calisthenics Plan")
                .durationWeeks(4)
                .splitType(WorkoutSplitType.PPL)
                .description("Newly created routine")
                .build();
        doReturn(mockPreview).when(aiWorkoutPlanService).generatePlan(any(), any(), any());

        // 6. "Create a new workout plan" -> create_workout_plan
        AiMessageResponse createResp = sendMsg(tokenWithPlan, convId, "Create a new workout plan.");
        assertThat(createResp.getIntent()).isEqualTo("WORKOUT_PLAN");
        assertThat(createResp.getMetadata()).contains("create_workout_plan");

        // 7a. "Replace my current plan" -> regenerate_workout_plan
        AiMessageResponse replaceResp = sendMsg(tokenWithPlan, convId, "Replace my current plan.");
        assertThat(replaceResp.getIntent()).isEqualTo("WORKOUT_PLAN");
        assertThat(replaceResp.getMetadata()).contains("regenerate_workout_plan");
        verify(aiWorkoutPlanService, times(2)).generatePlan(any(), any(), any());

        // 7b. "Change Monday's workout to Push Day" -> update_workout_plan
        AiMessageResponse updateDayResp = sendMsg(tokenWithPlan, convId, "Change Monday's workout to Push Day");
        assertThat(updateDayResp.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(updateDayResp.getMetadata()).contains("update_workout_plan");
        assertThat(updateDayResp.getContent()).contains("Push Day");

        // 7c. "Add 2 leg exercises to Wednesday" -> add_exercise_to_workout
        AiMessageResponse addLegResp = sendMsg(tokenWithPlan, convId, "Add 2 leg exercises to Wednesday");
        assertThat(addLegResp.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(addLegResp.getMetadata()).contains("add_exercise_to_workout");
        assertThat(addLegResp.getContent()).contains("Pistol Squat");
        assertThat(addLegResp.getContent()).contains("Bulgarian Split Squat");

        // 7d. "Remove Push-Up from Monday" -> remove_exercise_from_workout
        AiMessageResponse removeResp = sendMsg(tokenWithPlan, convId, "Remove Push-Up from Monday");
        assertThat(removeResp.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(removeResp.getMetadata()).contains("remove_exercise_from_workout");
        assertThat(removeResp.getContent()).contains("Removed **Push-Up**");
    }

    @Test
    @DisplayName("Criteria 8: User with no assigned or active plan gets an informative message without creating a plan")
    void userWithNoPlanReceivesInformativeMessageWithoutCreatingPlan() throws Exception {
        long initialCount = workoutPlanRepository.count();
        Long convId = createConv(tokenWithoutPlan);

        AiMessageResponse resp = sendMsg(tokenWithoutPlan, convId, "Show my workout plan.");
        assertThat(resp.getIntent()).isEqualTo("WORKOUT_PLAN_VIEW");
        assertThat(resp.getContent().toLowerCase()).contains("no assigned or active workout plan");
        assertThat(resp.getMetadata()).contains("\"found\":false");

        verify(aiWorkoutPlanService, never()).generatePlan(any(), any(), any());
        assertThat(workoutPlanRepository.count()).isEqualTo(initialCount);
    }

    @Test
    @DisplayName("Criteria 9: Retrieval failure reports error cleanly and NEVER falls back to plan creation")
    void retrievalFailureReportsErrorAndNeverCreatesPlan() throws Exception {
        long initialCount = workoutPlanRepository.count();
        Long convId = createConv(tokenWithPlan);

        // Simulate retrieval failure from tool service
        doThrow(new RuntimeException("Simulated DB timeout"))
                .when(userAiWorkoutToolService).getMyWorkoutPlan(anyLong(), anyBoolean(), anyBoolean());

        AiMessageResponse resp = sendMsg(tokenWithPlan, convId, "Show my workout plan.");
        assertThat(resp.getIntent()).isEqualTo("WORKOUT_PLAN_VIEW");
        assertThat(resp.getContent()).contains("error while retrieving your current workout plan");
        assertThat(resp.getMetadata()).contains("\"error\":true");

        verify(aiWorkoutPlanService, never()).generatePlan(any(), any(), any());
        assertThat(workoutPlanRepository.count()).isEqualTo(initialCount);
    }

    private AiMessageResponse sendMsgWithSelectedDay(
            String token, Long convId, String msg, WorkoutDayOfWeek selectedDay) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations/" + convId + "/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SendAiMessageRequest(msg, "UTC", selectedDay))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), AiMessageResponse.class);
    }

    private List<String> exerciseNamesForDay(Long planId, WorkoutDayOfWeek dow) {
        WorkoutPlanDay day = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(planId).stream()
                .filter(d -> d.getDayOfWeek() == dow)
                .findFirst()
                .orElseThrow();
        return workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(day.getId()).stream()
                .map(pe -> pe.getExercise().getName())
                .toList();
    }

    private WorkoutPlan setupPlancheFrontLeverPlan() {
        Exercise straddlePlanche = exerciseRepository.save(Exercise.builder()
                .name("Straddle Planche").code("STRADDLE_PLANCHE")
                .category(ExerciseCategory.SKILL).difficulty(DifficultyLevel.ADVANCED)
                .description("Straddle planche hold").instructions("Protract scapula and straddle legs.")
                .status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build());
        Exercise advTuckPlanche = exerciseRepository.save(Exercise.builder()
                .name("Advanced Tuck Planche").code("ADV_TUCK_PLANCHE")
                .category(ExerciseCategory.SKILL).difficulty(DifficultyLevel.ADVANCED)
                .description("Adv tuck planche hold").instructions("Maintain flat back and 90 degree hip angle.")
                .status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build());
        Exercise frontLever = exerciseRepository.save(Exercise.builder()
                .name("Front Lever").code("FRONT_LEVER")
                .category(ExerciseCategory.SKILL).difficulty(DifficultyLevel.ADVANCED)
                .description("Front lever hold").instructions("Depress scapula and hold horizontal.")
                .status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build());
        exerciseRepository.save(Exercise.builder()
                .name("Front Lever Raises").code("FRONT_LEVER_RAISES")
                .category(ExerciseCategory.PULL).difficulty(DifficultyLevel.INTERMEDIATE)
                .description("Dynamic straight-arm pulling raise").instructions(
                        "Start from an active hang with depressed shoulders and locked elbows. Raise your body toward horizontal or inverted position using controlled shoulder extension. Lower slowly with a 3-second eccentric.")
                .status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build());

        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athleteWithoutPlan)
                .name("planche front lever focus")
                .description("Planche and Front Lever specialization")
                .splitType(WorkoutSplitType.PPL)
                .active(true)
                .build();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan)
                    .dayOfWeek(dow)
                    .displayOrder(dow.getOrder())
                    .restDay(dow == WorkoutDayOfWeek.SUNDAY)
                    .name(dow == WorkoutDayOfWeek.TUESDAY ? "Pull" : null)
                    .description(dow == WorkoutDayOfWeek.TUESDAY ? "Front lever focus" : null)
                    .sessionType(switch (dow) {
                        case MONDAY, THURSDAY -> WorkoutSessionType.PUSH;
                        case TUESDAY, FRIDAY -> WorkoutSessionType.PULL;
                        case WEDNESDAY, SATURDAY -> WorkoutSessionType.LEGS;
                        case SUNDAY -> WorkoutSessionType.REST;
                    })
                    .build();
            if (dow == WorkoutDayOfWeek.MONDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(advTuckPlanche)
                        .sets(3).durationSeconds(15).restSeconds(60).displayOrder(1).build());
            } else if (dow == WorkoutDayOfWeek.TUESDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(frontLever)
                        .sets(3).durationSeconds(15).restSeconds(90).displayOrder(1).build());
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(tuckPlanche)
                        .sets(3).durationSeconds(15).restSeconds(90).displayOrder(2).build());
            } else if (dow == WorkoutDayOfWeek.THURSDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(straddlePlanche)
                        .sets(3).durationSeconds(15).restSeconds(60).displayOrder(1).build());
            } else if (dow == WorkoutDayOfWeek.FRIDAY) {
                day.getExercises().add(WorkoutPlanExercise.builder()
                        .workoutPlanDay(day).exercise(frontLever)
                        .sets(3).durationSeconds(15).restSeconds(60).displayOrder(1).build());
            }
            plan.getDays().add(day);
        }
        return workoutPlanRepository.save(plan);
    }

    @Test
    @DisplayName("Test cases 1-4 & Rules 1-7: Explicit weekday priority, strict exercise matching, no substitution, move between days, and verified persistence")
    void weekdayAssignmentMoveAndNoSubstitutionWorkflow() throws Exception {
        WorkoutPlan plan = setupPlancheFrontLeverPlan();
        Long convId = createConv(tokenWithoutPlan);

        // Even if Gemini hallucinates MONDAY, explicit Tuesday in user prompt MUST win (Rule 1 & 2)
        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.of(
                GeminiAssistantOutput.builder()
                        .intent("WORKOUT_PLAN_UPDATE")
                        .workoutPlanAction(GeminiAssistantOutput.WorkoutPlanAction.builder()
                                .toolName("add_exercise_to_workout")
                                .dayOfWeek("MONDAY")
                                .build())
                        .build()
        ));

        // Case 1: "Add Front Lever Raises to Tuesday" -> Tuesday only (even when selectedDayOfWeek = MONDAY)
        AiMessageResponse addTue = sendMsgWithSelectedDay(
                tokenWithoutPlan, convId, "Add Front Lever Raises (3 sets × 10 reps, 90s rest) to Tuesday", WorkoutDayOfWeek.MONDAY);
        assertThat(addTue.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(addTue.getMetadata()).contains("\"dayOfWeek\":\"TUESDAY\"");
        assertThat(addTue.getMetadata()).contains("\"addedExercises\":[\"Front Lever Raises\"]");
        assertThat(addTue.getContent()).contains("Tuesday");
        assertThat(addTue.getContent()).contains("Front Lever Raises");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.MONDAY))
                .containsExactly("Advanced Tuck Planche");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.THURSDAY))
                .containsExactly("Straddle Planche");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.FRIDAY))
                .containsExactly("Front Lever");

        // Verify prescription persisted as 3 sets x 10 reps, 90s rest
        WorkoutPlanDay tueDay = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(plan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.TUESDAY).findFirst().orElseThrow();
        WorkoutPlanExercise flr = workoutPlanExerciseRepository.findByWorkoutPlanDayIdOrderByDisplayOrderAsc(tueDay.getId()).stream()
                .filter(pe -> pe.getExercise().getName().equals("Front Lever Raises")).findFirst().orElseThrow();
        assertThat(flr.getSets()).isEqualTo(3);
        assertThat(flr.getReps()).isEqualTo(10);
        assertThat(flr.getRestSeconds()).isEqualTo(90);

        // Rule 5: Repeating "add to tuesday front lever  raise" (singular) must NOT substitute Tuck Planche or any other exercise
        AiMessageResponse dupTue = sendMsg(tokenWithoutPlan, convId, "add to tuesday front lever  raise");
        assertThat(dupTue.getMetadata()).contains("\"mutated\":false");
        assertThat(dupTue.getContent()).contains("already scheduled on **Tuesday**");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");

        // Case 2: "Remove Tuck Planche from Tuesday" -> Tuesday only
        AiMessageResponse remTue = sendMsgWithSelectedDay(
                tokenWithoutPlan, convId, "Remove Tuck Planche from Tuesday", WorkoutDayOfWeek.MONDAY);
        assertThat(remTue.getMetadata()).contains("\"dayOfWeek\":\"TUESDAY\"");
        assertThat(remTue.getMetadata()).contains("\"removedExercise\":\"Tuck Planche\"");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Front Lever Raises");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.MONDAY))
                .containsExactly("Advanced Tuck Planche");

        // Removing Tuck Planche from Tuesday again when not on Tuesday must NOT remove from another day
        AiMessageResponse remTueAgain = sendMsg(tokenWithoutPlan, convId, "Remove Tuck Planche from Tuesday");
        assertThat(remTueAgain.getMetadata()).contains("\"mutated\":false");
        assertThat(remTueAgain.getContent()).contains("not found on **Tuesday**");

        // Case 4: "Add Straddle Planche to Monday" -> Monday only
        AiMessageResponse addMon = sendMsgWithSelectedDay(
                tokenWithoutPlan, convId, "Add Straddle Planche to Monday", WorkoutDayOfWeek.TUESDAY);
        assertThat(addMon.getMetadata()).contains("\"dayOfWeek\":\"MONDAY\"");
        assertThat(addMon.getMetadata()).contains("\"addedExercises\":[\"Straddle Planche\"]");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.MONDAY))
                .containsExactly("Advanced Tuck Planche", "Straddle Planche");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Front Lever Raises");

        // Case 3: Prepare Front Lever Raises on Monday (and remove from Tuesday), then "Move Front Lever Raises from Monday to Tuesday"
        sendMsg(tokenWithoutPlan, convId, "Remove Front Lever Raises from Tuesday");
        sendMsg(tokenWithoutPlan, convId, "Add Front Lever Raises (3 sets × 10 reps, 90s rest) to Monday");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.MONDAY))
                .containsExactly("Advanced Tuck Planche", "Straddle Planche", "Front Lever Raises");

        AiMessageResponse moveResp = sendMsg(tokenWithoutPlan, convId, "Move Front Lever Raises from Monday to Tuesday");
        assertThat(moveResp.getIntent()).isEqualTo("WORKOUT_PLAN_UPDATE");
        assertThat(moveResp.getMetadata()).contains("\"toolName\":\"move_exercise_between_days\"");
        assertThat(moveResp.getMetadata()).contains("\"sourceDayOfWeek\":\"MONDAY\"");
        assertThat(moveResp.getMetadata()).contains("\"dayOfWeek\":\"TUESDAY\"");
        assertThat(moveResp.getMetadata()).contains("\"movedExercise\":\"Front Lever Raises\"");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.MONDAY))
                .containsExactly("Advanced Tuck Planche", "Straddle Planche");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Front Lever Raises");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.THURSDAY))
                .containsExactly("Straddle Planche");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.FRIDAY))
                .containsExactly("Front Lever");
    }

    @Test
    @DisplayName("Regression: Negated weekday 'no, add to Tuesday not monday', uncataloged exercises, and word-boundary safety")
    void negatedWeekdayAndNoSubstitutionRegression() throws Exception {
        WorkoutPlan plan = setupPlancheFrontLeverPlan();
        Long convId = createConv(tokenWithoutPlan);

        // Negated Monday must extract TUESDAY, never MONDAY, and never add Tuck Planche when no exercise is named
        assertThat(UserAiWorkoutToolService.extractWeekday("no, add to Tuesday not monday"))
                .isEqualTo(WorkoutDayOfWeek.TUESDAY);
        assertThat(UserAiWorkoutToolService.extractWeekday("Add to Tuesday instead of Monday"))
                .isEqualTo(WorkoutDayOfWeek.TUESDAY);
        assertThat(UserAiWorkoutToolService.extractWeekday("What are common mistakes in Push-Up?")).isNull();
        assertThat(UserAiWorkoutToolService.extractWeekday("I sat down and showed progress")).isNull();

        // Turn 1 without weekday asks clarification instead of corrupting Monday
        AiMessageResponse askDay = sendMsg(tokenWithoutPlan, convId, "Add Front Lever Raises");
        assertThat(askDay.getMetadata()).contains("\"type\":\"WORKOUT_PLAN_CLARIFICATION\"");
        assertThat(askDay.getMetadata()).contains("\"mutated\":false");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.MONDAY))
                .containsExactly("Advanced Tuck Planche");

        // Turn 2 "no, add to Tuesday not monday" completes adding Front Lever Raises to TUESDAY only!
        AiMessageResponse followUpTue = sendMsg(tokenWithoutPlan, convId, "no, add to Tuesday not monday");
        assertThat(followUpTue.getMetadata()).contains("\"dayOfWeek\":\"TUESDAY\"");
        assertThat(followUpTue.getMetadata()).contains("\"addedExercises\":[\"Front Lever Raises\"]");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.MONDAY))
                .containsExactly("Advanced Tuck Planche");

        // Uncataloged exercise must NOT substitute Front Lever or Tuck Planche
        AiMessageResponse uncataloged = sendMsg(tokenWithoutPlan, convId, "Add Front Lever Negative Pulls to Tuesday");
        assertThat(uncataloged.getMetadata()).contains("\"mutated\":false");
        assertThat(exerciseNamesForDay(plan.getId(), WorkoutDayOfWeek.TUESDAY))
                .containsExactly("Front Lever", "Tuck Planche", "Front Lever Raises");
    }
}
