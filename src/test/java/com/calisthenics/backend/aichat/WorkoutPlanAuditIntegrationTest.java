package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.calisthenics.backend.aichat.dto.CreateAiConversationRequest;
import com.calisthenics.backend.aichat.dto.SendAiMessageRequest;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.bodymetrics.entity.ClientBodyMeasurement;
import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
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
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSource;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WorkoutPlanAuditIntegrationTest {

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
    @Autowired private ClientBodyMeasurementRepository measurementRepository;
    @Autowired private PersonalRecordRepository personalRecordRepository;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Autowired private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;

    @MockBean private GeminiApiClient geminiApiClient;

    private User athlete;
    private String athleteToken;

    private Exercise straddlePlanche;
    private Exercise tuckPlanche;
    private Exercise frontLever;
    private Exercise frontLeverRaises;
    private Exercise pullUp;
    private Exercise pushUp;
    private Exercise dips;
    private Exercise hspu;
    private Exercise pistolSquat;
    private Exercise romanianDeadlift;

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
                .email("audit_athlete_" + ts + "@test.com")
                .fullName("Audit Athlete")
                .role(Role.ROLE_USER)
                .build());
        athleteToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athlete));

        straddlePlanche = saveEx("Straddle Planche", "STRADDLE_PLANCHE", ExerciseCategory.SKILL, DifficultyLevel.ADVANCED,
                "Front Deltoids", "Chest, Triceps, Rectus Abdominis");
        tuckPlanche = saveEx("Tuck Planche", "TUCK_PLANCHE", ExerciseCategory.ISOMETRIC, DifficultyLevel.INTERMEDIATE,
                "Front Deltoids", "Chest, Triceps, Rectus Abdominis");
        frontLever = saveEx("Front Lever", "FRONT_LEVER", ExerciseCategory.SKILL, DifficultyLevel.ADVANCED,
                "Latissimus Dorsi, Teres Major", "Rhomboids, Rear Deltoids, Rectus Abdominis");
        frontLeverRaises = saveEx("Front Lever Raises", "FRONT_LEVER_RAISES", ExerciseCategory.PULL, DifficultyLevel.INTERMEDIATE,
                "Latissimus Dorsi, Teres Major", "Rhomboids, Rear Deltoids");
        pullUp = saveEx("Pull-Up", "PULL_UP", ExerciseCategory.PULL, DifficultyLevel.INTERMEDIATE,
                "Latissimus Dorsi", "Biceps, Rhomboids");
        pushUp = saveEx("Push-Up", "PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.BEGINNER,
                "Chest, Front Deltoids", "Triceps, Rectus Abdominis");
        dips = saveEx("Dips", "DIPS", ExerciseCategory.PUSH, DifficultyLevel.INTERMEDIATE,
                "Triceps, Chest", "Front Deltoids");
        hspu = saveEx("Handstand Push-Up", "HANDSTAND_PUSH_UP", ExerciseCategory.PUSH, DifficultyLevel.ADVANCED,
                "Front Deltoids, Triceps", "Chest, Upper Trapezius");
        pistolSquat = saveEx("Pistol Squat", "PISTOL_SQUAT", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Quadriceps, Glutes", "Hamstrings, Calves");
        romanianDeadlift = saveEx("Single-Leg Romanian Deadlift", "SINGLE_LEG_RDL", ExerciseCategory.LEGS, DifficultyLevel.INTERMEDIATE,
                "Hamstrings, Glutes", "Erector Spinae");

        when(geminiApiClient.callAssistant(anyString(), anyList())).thenReturn(Optional.empty());
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private Exercise saveEx(String name, String code, ExerciseCategory cat, DifficultyLevel diff, String pri, String sec) {
        return exerciseRepository.save(Exercise.builder()
                .name(name).code(code).category(cat).difficulty(diff)
                .targetMuscles(pri).secondaryMuscles(sec)
                .status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build());
    }

    private void addDayEx(WorkoutPlanDay day, Exercise ex, int sets, Integer reps, Integer durSec, int order) {
        day.getExercises().add(WorkoutPlanExercise.builder()
                .workoutPlanDay(day).exercise(ex).sets(sets).reps(reps).durationSeconds(durSec)
                .restSeconds(90).displayOrder(order).build());
    }

    private Long createConversation() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateAiConversationRequest("Audit Flow"))))
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

    @Test
    @DisplayName("Scenarios 1, 2, 4, 5 & 7: Full athlete & 'Planche Front Lever Focus' audit, compound coverage, missing muscle gaps, and live DB updates")
    void scenarios1_2_4_5_7_comprehensiveAuditAndDynamicUpdates() throws Exception {
        clientProfileRepository.save(ClientProfile.builder()
                .user(athlete)
                .experienceLevel("ADVANCED")
                .primaryGoals("Straddle Planche and Full Front Lever")
                .preferredFrequency("5 days/week")
                .heightCm(BigDecimal.valueOf(173.0))
                .build());
        measurementRepository.save(ClientBodyMeasurement.builder()
                .client(athlete)
                .weightKg(BigDecimal.valueOf(68.5))
                .measuredAt(ZonedDateTime.now().minusDays(1))
                .build());

        personalRecordRepository.save(PersonalRecord.builder()
                .user(athlete).exercise(straddlePlanche).recordType(PersonalRecordType.MAX_HOLD)
                .recordValue(BigDecimal.valueOf(8)).unit("s").achievedAt(ZonedDateTime.now().minusDays(3)).build());
        personalRecordRepository.save(PersonalRecord.builder()
                .user(athlete).exercise(frontLever).recordType(PersonalRecordType.MAX_HOLD)
                .recordValue(BigDecimal.valueOf(12)).unit("s").achievedAt(ZonedDateTime.now().minusDays(2)).build());
        personalRecordRepository.save(PersonalRecord.builder()
                .user(athlete).customExerciseName("Weighted Pull-Up").recordType(PersonalRecordType.MAX_WEIGHT)
                .recordValue(BigDecimal.valueOf(35)).unit("kg").secondaryValue(BigDecimal.valueOf(5))
                .achievedAt(ZonedDateTime.now().minusDays(4)).build());

        workoutSessionRepository.save(WorkoutSession.builder()
                .user(athlete).title("Tuesday Pull").workoutDate(LocalDate.now().minusDays(2))
                .status(WorkoutSessionStatus.COMPLETED).source(WorkoutSessionSource.PERSONAL).build());
        workoutSessionRepository.save(WorkoutSession.builder()
                .user(athlete).title("Incomplete Scheduled").workoutDate(LocalDate.now())
                .status(WorkoutSessionStatus.IN_PROGRESS).source(WorkoutSessionSource.PERSONAL).build());

        // Planche Front Lever Focus plan:
        // Triceps has NO isolated exercise, but gets >6 effective sets via Dips (primary) + Push-Up + HSPU + Straddle Planche (compound coverage!)
        // Hamstrings only gets weak secondary stimulus from Pistol Squat -> UNDERDEVELOPED
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athlete)
                .name("Planche Front Lever Focus")
                .description("Specialized straight-arm and strength program")
                .splitType(WorkoutSplitType.CUSTOM)
                .active(true)
                .build();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean rest = dow == WorkoutDayOfWeek.THURSDAY || dow == WorkoutDayOfWeek.SUNDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan).dayOfWeek(dow).displayOrder(dow.getOrder()).restDay(rest)
                    .name(switch (dow) {
                        case MONDAY -> "Planche + Push";
                        case TUESDAY -> "Front Lever + Pull";
                        case WEDNESDAY -> "Legs";
                        case FRIDAY -> "HSPU + Dips";
                        case SATURDAY -> "Pull + Front Lever";
                        default -> "Rest Day";
                    })
                    .sessionType(switch (dow) {
                        case MONDAY, FRIDAY -> WorkoutSessionType.PUSH;
                        case TUESDAY, SATURDAY -> WorkoutSessionType.PULL;
                        case WEDNESDAY -> WorkoutSessionType.LEGS;
                        default -> WorkoutSessionType.REST;
                    })
                    .build();
            switch (dow) {
                case MONDAY -> {
                    addDayEx(day, straddlePlanche, 4, null, 10, 1);
                    addDayEx(day, pushUp, 4, 12, null, 2);
                }
                case TUESDAY -> {
                    addDayEx(day, frontLever, 4, null, 12, 1);
                    addDayEx(day, pullUp, 4, 8, null, 2);
                }
                case WEDNESDAY -> addDayEx(day, pistolSquat, 4, 8, null, 1);
                case FRIDAY -> {
                    addDayEx(day, hspu, 4, 6, null, 1);
                    addDayEx(day, dips, 4, 10, null, 2);
                }
                case SATURDAY -> {
                    addDayEx(day, frontLeverRaises, 4, 8, null, 1);
                    addDayEx(day, pullUp, 4, 8, null, 2);
                }
                default -> {}
            }
            plan.getDays().add(day);
        }
        plan = workoutPlanRepository.save(plan);

        Long convId = createConversation();

        // Scenario 1 + 2: "Is my workout missing anything?"
        AiMessageResponse auditResp = sendMessage(convId, "Is my workout missing anything?");
        assertThat(auditResp.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(auditResp.getContent()).contains("Planche Front Lever Focus");
        assertThat(auditResp.getContent()).contains("68.5 kg");
        assertThat(auditResp.getContent()).contains("173.0 cm");
        assertThat(auditResp.getContent()).contains("1 completed session(s)");
        assertThat(auditResp.getContent()).contains("1 incomplete/scheduled session(s) excluded");
        assertThat(auditResp.getContent()).contains("Straddle Planche: 8s");
        assertThat(auditResp.getContent()).contains("Front Lever: 12s");
        assertThat(auditResp.getContent()).contains("Weighted Pull-Up: 35 kg");
        assertThat(auditResp.getContent()).contains("Would you like me to suggest specific exercises and where to integrate them?");

        JsonNode meta = objectMapper.readTree(auditResp.getMetadata());
        assertThat(meta.path("readOnly").asBoolean()).isTrue();
        assertThat(meta.path("mutated").asBoolean()).isFalse();
        assertThat(meta.path("specializedProgram").asBoolean()).isTrue();
        assertThat(meta.path("programPriorities").toString()).contains("Planche", "Front Lever");

        // Scenario 4: Hamstrings has low effective volume -> reported in undertrainedMuscles
        assertThat(meta.path("undertrainedMuscles").toString()).contains("Hamstrings");

        // Scenario 5: Triceps has NO isolated triceps exercise, yet is adequately covered via Dips/Push-Up/HSPU/Planche!
        assertThat(meta.path("undertrainedMuscles").toString()).doesNotContain("Triceps");
        assertThat(meta.path("adequatelyCoveredMuscles").toString()).contains("Triceps");

        // Follow-up "Yes" to "Would you like me to suggest specific exercises and where to integrate them?" -> EXERCISE_RECOMMENDATION
        AiMessageResponse followUpRec = sendMessage(convId, "Yes");
        assertThat(followUpRec.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(followUpRec.getMetadata()).contains("\"type\":\"EXERCISE_RECOMMENDATIONS\"");
        assertThat(followUpRec.getMetadata()).contains("\"mutated\":false");

        // Also test other audit question phrasing: "What weaknesses do you see in my current program?"
        AiMessageResponse altPhrase = sendMessage(convId, "What weaknesses do you see in my current program?");
        assertThat(altPhrase.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(altPhrase.getMetadata()).contains("Planche Front Lever Focus");

        // Scenario 7: User updates body weight, logs a new PR, and adds Romanian Deadlift to Wednesday -> next audit reflects changes immediately!
        measurementRepository.save(ClientBodyMeasurement.builder()
                .client(athlete).weightKg(BigDecimal.valueOf(67.0)).measuredAt(ZonedDateTime.now()).build());
        personalRecordRepository.save(PersonalRecord.builder()
                .user(athlete).exercise(straddlePlanche).recordType(PersonalRecordType.MAX_HOLD)
                .recordValue(BigDecimal.valueOf(11)).unit("s").achievedAt(ZonedDateTime.now()).build());
        WorkoutPlanDay wedDay = workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(plan.getId()).stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.WEDNESDAY).findFirst().orElseThrow();
        workoutPlanExerciseRepository.save(WorkoutPlanExercise.builder()
                .workoutPlanDay(wedDay).exercise(romanianDeadlift).sets(4).reps(10).restSeconds(90).displayOrder(2).build());

        AiMessageResponse updatedAudit = sendMessage(convId, "Am I neglecting any muscle groups?");
        assertThat(updatedAudit.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(updatedAudit.getContent()).contains("67.0 kg");
        assertThat(updatedAudit.getContent()).contains("Straddle Planche: 11s");
        JsonNode updatedMeta = objectMapper.readTree(updatedAudit.getMetadata());
        assertThat(updatedMeta.path("undertrainedMuscles").toString()).doesNotContain("Hamstrings");
        assertThat(updatedMeta.path("adequatelyCoveredMuscles").toString()).contains("Hamstrings");
    }

    @Test
    @DisplayName("Scenario 3: Strong Planche PR with static holds only and weak supporting pressing/core identifies supporting weaknesses")
    void scenario3_strongPlanchePrWithWeakSupportingWork_identifiesSupportingBottlenecks() throws Exception {
        clientProfileRepository.save(ClientProfile.builder()
                .user(athlete).experienceLevel("ADVANCED").primaryGoals("Full Planche").build());
        personalRecordRepository.save(PersonalRecord.builder()
                .user(athlete).exercise(straddlePlanche).recordType(PersonalRecordType.MAX_HOLD)
                .recordValue(BigDecimal.valueOf(12)).unit("s").achievedAt(ZonedDateTime.now().minusDays(1)).build());

        // Plan has ONLY static Straddle Planche + Tuck Planche holds on push days (0 bent-arm push, 0 core)
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athlete).name("Planche Focus").splitType(WorkoutSplitType.CUSTOM).active(true).build();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean rest = dow == WorkoutDayOfWeek.WEDNESDAY || dow == WorkoutDayOfWeek.SATURDAY || dow == WorkoutDayOfWeek.SUNDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan).dayOfWeek(dow).displayOrder(dow.getOrder()).restDay(rest)
                    .name(rest ? "Rest Day" : (dow == WorkoutDayOfWeek.TUESDAY || dow == WorkoutDayOfWeek.FRIDAY ? "Pull" : "Planche Hold"))
                    .sessionType(rest ? WorkoutSessionType.REST : (dow == WorkoutDayOfWeek.TUESDAY || dow == WorkoutDayOfWeek.FRIDAY ? WorkoutSessionType.PULL : WorkoutSessionType.PLANCHE))
                    .build();
            if (dow == WorkoutDayOfWeek.MONDAY || dow == WorkoutDayOfWeek.THURSDAY) {
                addDayEx(day, straddlePlanche, 4, null, 10, 1);
                addDayEx(day, tuckPlanche, 3, null, 15, 2);
            } else if (dow == WorkoutDayOfWeek.TUESDAY || dow == WorkoutDayOfWeek.FRIDAY) {
                addDayEx(day, pullUp, 4, 8, null, 1);
            }
            plan.getDays().add(day);
        }
        workoutPlanRepository.save(plan);

        Long convId = createConversation();
        AiMessageResponse resp = sendMessage(convId, "What should I improve in my routine?");

        assertThat(resp.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(resp.getContent()).contains("Strong PR but Supporting Strength Gap");
        assertThat(resp.getContent()).contains("rather than adding more static Planche holds");
        assertThat(resp.getContent()).contains("Pseudo Planche Push-Ups");
    }

    @Test
    @DisplayName("Scenario 6: Insufficient athlete data (no weight/height/PRs, incomplete sessions only) is explicitly acknowledged without fabricating metrics")
    void scenario6_insufficientData_acknowledgesGapsAndIgnoresIncompleteSessions() throws Exception {
        // Minimal plan, NO ClientBodyMeasurement, NO ClientProfile, NO PRs, ONLY an IN_PROGRESS session
        workoutSessionRepository.save(WorkoutSession.builder()
                .user(athlete).title("Half-done session").workoutDate(LocalDate.now())
                .status(WorkoutSessionStatus.IN_PROGRESS).source(WorkoutSessionSource.PERSONAL).build());

        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athlete).name("Starter Routine").splitType(WorkoutSplitType.FULL_BODY).active(true).build();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean rest = dow != WorkoutDayOfWeek.MONDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan).dayOfWeek(dow).displayOrder(dow.getOrder()).restDay(rest)
                    .name(rest ? "Rest Day" : "Full Body").sessionType(rest ? WorkoutSessionType.REST : WorkoutSessionType.FULL_BODY)
                    .build();
            if (!rest) {
                addDayEx(day, pushUp, 3, 10, null, 1);
                addDayEx(day, pullUp, 3, 8, null, 2);
            }
            plan.getDays().add(day);
        }
        workoutPlanRepository.save(plan);

        Long convId = createConversation();
        AiMessageResponse resp = sendMessage(convId, "Is my training balanced?");

        assertThat(resp.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        assertThat(resp.getContent()).contains("Weight not recorded");
        assertThat(resp.getContent()).contains("Height not recorded");
        assertThat(resp.getContent()).contains("0 completed session(s)");
        assertThat(resp.getContent()).contains("1 incomplete/scheduled session(s) excluded");
        assertThat(resp.getContent()).contains("Personal Records (PRs)**: None recorded yet");
        assertThat(resp.getContent()).contains("Data Gaps Acknowledged");
    }

    @Test
    @DisplayName("Consultation follow-up recommendation buttons pass muscle group, include routine context, and preserve history")
    void consultationRecommendationFollowUp_includesRoutineContextTargetsMuscleGroupAndPreservesHistory() throws Exception {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athlete).name("Push Pull Leg Split").splitType(WorkoutSplitType.PPL).active(true).build();
        for (WorkoutDayOfWeek dow : WorkoutDayOfWeek.values()) {
            boolean rest = dow == WorkoutDayOfWeek.THURSDAY || dow == WorkoutDayOfWeek.SUNDAY;
            WorkoutPlanDay day = WorkoutPlanDay.builder()
                    .workoutPlan(plan).dayOfWeek(dow).displayOrder(dow.getOrder()).restDay(rest)
                    .name(switch (dow) {
                        case MONDAY -> "Push Day";
                        case TUESDAY -> "Pull Day";
                        case WEDNESDAY -> "Leg Day";
                        case FRIDAY -> "Upper Push";
                        case SATURDAY -> "Upper Pull";
                        default -> "Rest Day";
                    })
                    .sessionType(switch (dow) {
                        case MONDAY, FRIDAY -> WorkoutSessionType.PUSH;
                        case TUESDAY, SATURDAY -> WorkoutSessionType.PULL;
                        case WEDNESDAY -> WorkoutSessionType.LEGS;
                        default -> WorkoutSessionType.REST;
                    })
                    .build();
            if (dow == WorkoutDayOfWeek.MONDAY || dow == WorkoutDayOfWeek.FRIDAY) {
                addDayEx(day, pushUp, 4, 12, null, 1);
            } else if (dow == WorkoutDayOfWeek.TUESDAY || dow == WorkoutDayOfWeek.SATURDAY) {
                addDayEx(day, pullUp, 4, 8, null, 1);
            } else if (dow == WorkoutDayOfWeek.WEDNESDAY) {
                addDayEx(day, pistolSquat, 4, 8, null, 1);
            }
            plan.getDays().add(day);
        }
        workoutPlanRepository.save(plan);

        Long convId = createConversation();

        // 1. User asks if current workout routine is balanced
        AiMessageResponse auditResp = sendMessage(convId, "📊 Is my current workout routine balanced?");
        assertThat(auditResp.getIntent()).isEqualTo("WORKOUT_CONSULTATION");
        JsonNode auditMeta = objectMapper.readTree(auditResp.getMetadata());
        assertThat(auditMeta.path("undertrainedMuscles").toString()).contains("Hamstrings");

        // 2. Click muscle recommendation button with explicit routine context phrasing
        AiMessageResponse hamResp = sendMessage(convId, "Recommend exercise for Hamstrings for my current routine");
        assertThat(hamResp.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        JsonNode hamMeta = objectMapper.readTree(hamResp.getMetadata());
        assertThat(hamMeta.path("informationalOnly").asBoolean()).isFalse();
        assertThat(hamMeta.path("focusLabel").asText()).containsIgnoringCase("Hamstrings");
        assertThat(hamMeta.path("suggestedDayOfWeek").asText()).isEqualTo("WEDNESDAY");
        assertThat(hamMeta.path("availablePlanDays")).isNotEmpty();
        assertThat(hamMeta.path("recommendations").findValuesAsText("name"))
                .contains("Single-Leg Romanian Deadlift");

        // 3. Click a second recommendation button from the earlier consultation (e.g. without routine suffix)
        AiMessageResponse chestResp = sendMessage(convId, "Recommend exercise for Chest");
        assertThat(chestResp.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        JsonNode chestMeta = objectMapper.readTree(chestResp.getMetadata());
        assertThat(chestMeta.path("informationalOnly").asBoolean()).isFalse();
        assertThat(chestMeta.path("focusLabel").asText()).isEqualTo("Chest");
        assertThat(chestMeta.path("suggestedDayOfWeek").asText()).isIn("MONDAY", "FRIDAY");
        assertThat(chestMeta.path("recommendations").findValuesAsText("name"))
                .contains("Dips");

        // 4. Verify full conversation history remains intact in exact order
        MvcResult convRes = mockMvc.perform(get("/api/v1/ai/conversations/" + convId)
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode convJson = objectMapper.readTree(convRes.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(convJson.path("messages").size()).isEqualTo(6);
    }
}
