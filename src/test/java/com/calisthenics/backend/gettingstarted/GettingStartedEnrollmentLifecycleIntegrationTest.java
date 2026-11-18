package com.calisthenics.backend.gettingstarted;

import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedEnrollment;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgram;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgramDay;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgramExercise;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedDayStatus;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedEnrollmentStatus;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedDayProgressRepository;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedEnrollmentRepository;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedProgramRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSource;
import com.calisthenics.backend.workout.dto.FinishWorkoutSessionRequest;
import com.calisthenics.backend.workout.dto.UpdateWorkoutSessionSetRequest;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GettingStartedEnrollmentLifecycleIntegrationTest {

    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "bulk_exercise_generation_items",
            "bulk_exercise_generation_jobs",
            "admin_ai_chat_idempotency",
            "shared_personal_records",
            "personal_records",
            "workout_logs",
            "getting_started_day_progress",
            "getting_started_day_completions",
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
            "coach_client_relationships",
            "workout_plan_exercises",
            "workout_plan_days",
            "workout_plans",
            "exercises",
            "users"
    );

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    @Autowired private UserRepository userRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private GettingStartedProgramRepository programRepository;
    @Autowired private GettingStartedEnrollmentRepository enrollmentRepository;
    @Autowired private GettingStartedDayProgressRepository dayProgressRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private CoachClientRelationshipRepository relationshipRepository;
    @Autowired private WorkoutPlanAssignmentRepository assignmentRepository;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;

    private User adminUser;
    private User athleteUser;
    private User coachUser;
    private Exercise pushUp;
    private Exercise squat;
    private Exercise plank;
    private GettingStartedProgram publishedProgram;
    private String athleteToken;

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @BeforeEach
    void setUp() {
        cleanDb();

        adminUser = userRepository.save(User.builder()
                .email("admin.lifecycle@calisthenics.test")
                .passwordHash("$2a$10$AdminHash")
                .fullName("Program Admin")
                .role(Role.ROLE_ADMIN)
                .experienceLevel("ADVANCED")
                .build());

        athleteUser = userRepository.save(User.builder()
                .email("athlete.lifecycle@calisthenics.test")
                .passwordHash("$2a$10$AthleteHash")
                .fullName("Lifecycle Athlete")
                .role(Role.ROLE_USER)
                .experienceLevel("BEGINNER")
                .build());

        coachUser = userRepository.save(User.builder()
                .email("coach.lifecycle@calisthenics.test")
                .passwordHash("$2a$10$CoachHash")
                .fullName("Coach Maya")
                .role(Role.ROLE_USER)
                .experienceLevel("ELITE")
                .build());

        pushUp = exerciseRepository.save(Exercise.builder()
                .name("Push-Up")
                .code("GS_LC_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());

        squat = exerciseRepository.save(Exercise.builder()
                .name("Bodyweight Squat")
                .code("GS_LC_SQUAT")
                .category(ExerciseCategory.LEGS)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());

        plank = exerciseRepository.save(Exercise.builder()
                .name("Forearm Plank")
                .code("GS_LC_PLANK")
                .category(ExerciseCategory.CORE)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());

        publishedProgram = seedPublishedProgram();
        athleteToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(athleteUser));
    }

    private GettingStartedProgram seedPublishedProgram() {
        GettingStartedProgram program = GettingStartedProgram.builder()
                .title("3 Days Getting Started")
                .description("Foundation full-body intro")
                .targetAudience(TargetAudience.ALL_ATHLETES)
                .published(true)
                .version(1)
                .creator(adminUser)
                .publishedAt(ZonedDateTime.now())
                .build();

        GettingStartedProgramDay day1 = GettingStartedProgramDay.builder()
                .program(program)
                .dayNumber(1)
                .title("Day 1 · Upper Push Foundation")
                .description("Build clean push mechanics")
                .build();
        day1.getExercises().add(GettingStartedProgramExercise.builder()
                .programDay(day1)
                .exercise(pushUp)
                .exerciseName("Push-Up")
                .sets(2)
                .reps(8)
                .restSeconds(60)
                .displayOrder(1)
                .build());
        day1.getExercises().add(GettingStartedProgramExercise.builder()
                .programDay(day1)
                .exercise(plank)
                .exerciseName("Forearm Plank")
                .sets(1)
                .durationSeconds(30)
                .restSeconds(45)
                .displayOrder(2)
                .build());

        GettingStartedProgramDay day2 = GettingStartedProgramDay.builder()
                .program(program)
                .dayNumber(2)
                .title("Day 2 · Lower Body Control")
                .description("Controlled squat cadence")
                .build();
        day2.getExercises().add(GettingStartedProgramExercise.builder()
                .programDay(day2)
                .exercise(squat)
                .exerciseName("Bodyweight Squat")
                .sets(2)
                .reps(12)
                .restSeconds(60)
                .displayOrder(1)
                .build());

        GettingStartedProgramDay day3 = GettingStartedProgramDay.builder()
                .program(program)
                .dayNumber(3)
                .title("Day 3 · Full Body Integration")
                .description("Combine push and legs")
                .build();
        day3.getExercises().add(GettingStartedProgramExercise.builder()
                .programDay(day3)
                .exercise(pushUp)
                .exerciseName("Push-Up")
                .sets(2)
                .reps(10)
                .restSeconds(60)
                .displayOrder(1)
                .build());
        day3.getExercises().add(GettingStartedProgramExercise.builder()
                .programDay(day3)
                .exercise(squat)
                .exerciseName("Bodyweight Squat")
                .sets(2)
                .reps(12)
                .restSeconds(60)
                .displayOrder(2)
                .build());

        program.getDays().add(day1);
        program.getDays().add(day2);
        program.getDays().add(day3);
        return programRepository.save(program);
    }

    private WorkoutPlan createPersonalPlanForWeekday(WorkoutDayOfWeek weekday, String name) {
        WorkoutPlan plan = WorkoutPlan.builder()
                .user(athleteUser)
                .name(name)
                .splitType(WorkoutSplitType.CUSTOM)
                .active(true)
                .aiGenerated(false)
                .build();
        WorkoutPlanDay day = WorkoutPlanDay.builder()
                .workoutPlan(plan)
                .dayOfWeek(weekday)
                .sessionType(WorkoutSessionType.STRENGTH)
                .name("Personal Strength")
                .restDay(false)
                .displayOrder(weekday.getOrder())
                .build();
        plan.getDays().add(day);
        return workoutPlanRepository.save(plan);
    }

    private WorkoutPlanAssignment createCoachAssignmentForWeekday(WorkoutDayOfWeek weekday, String planName) {
        CoachClientRelationship relationship = relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coachUser)
                .client(athleteUser)
                .requestedBy(athleteUser)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());
        WorkoutPlan coachPlan = WorkoutPlan.builder()
                .user(coachUser)
                .name(planName)
                .splitType(WorkoutSplitType.CUSTOM)
                .active(true)
                .aiGenerated(false)
                .build();
        WorkoutPlanDay day = WorkoutPlanDay.builder()
                .workoutPlan(coachPlan)
                .dayOfWeek(weekday)
                .sessionType(WorkoutSessionType.FULL_BODY)
                .name("Coach Session")
                .restDay(false)
                .displayOrder(weekday.getOrder())
                .build();
        coachPlan.getDays().add(day);
        WorkoutPlan savedCoachPlan = workoutPlanRepository.save(coachPlan);

        return assignmentRepository.save(WorkoutPlanAssignment.builder()
                .relationship(relationship)
                .workoutPlan(savedCoachPlan)
                .coach(coachUser)
                .client(athleteUser)
                .status(WorkoutPlanAssignmentStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(2))
                .notes("Must never be overwritten")
                .build());
    }

    private JsonNode startAndFinishDayWithAllSetsCompleted(int dayNumber, LocalDate workoutDate) throws Exception {
        MvcResult startResult = mockMvc.perform(post("/api/v1/getting-started-program/days/{dayNumber}/start", dayNumber)
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("workoutDate", workoutDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("GETTING_STARTED"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.gettingStartedDayNumber").value(dayNumber))
                .andReturn();

        JsonNode session = objectMapper.readTree(startResult.getResponse().getContentAsString());
        long sessionId = session.get("id").asLong();

        for (JsonNode exercise : session.get("exercises")) {
            long exId = exercise.get("id").asLong();
            JsonNode targetRepsNode = exercise.get("targetReps");
            JsonNode targetHoldNode = exercise.get("targetHoldDurationSeconds");
            Integer reps = (targetRepsNode != null && !targetRepsNode.isNull()) ? targetRepsNode.asInt() : null;
            Integer hold = (targetHoldNode != null && !targetHoldNode.isNull()) ? targetHoldNode.asInt() : null;
            for (JsonNode set : exercise.get("sets")) {
                if (set.get("completed").asBoolean()) {
                    continue;
                }
                long setId = set.get("id").asLong();
                long version = set.get("version").asLong();
                UpdateWorkoutSessionSetRequest req = new UpdateWorkoutSessionSetRequest(
                        reps,
                        hold,
                        null,
                        null,
                        true,
                        null,
                        version
                );
                mockMvc.perform(put("/api/v1/workout-sessions/{sessionId}/exercises/{exerciseId}/sets/{setId}",
                                sessionId, exId, setId)
                                .header("Authorization", "Bearer " + athleteToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(req)))
                        .andExpect(status().isOk());
            }
        }

        MvcResult finishResult = mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/finish", sessionId)
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishWorkoutSessionRequest(false))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andReturn();
        return objectMapper.readTree(finishResult.getResponse().getContentAsString());
    }

    @Test
    void enrollNoJustStart_preservesPlans_requiresRealSessionCompletion_andPromotesToCompleted() throws Exception {
        LocalDate today = LocalDate.now();
        WorkoutDayOfWeek todayDow = WorkoutDayOfWeek.valueOf(today.getDayOfWeek().name());
        WorkoutPlan personalPlan = createPersonalPlanForWeekday(todayDow, "My Personal Calisthenics Split");

        // 1. User selects "No, Just Start"
        mockMvc.perform(post("/api/v1/getting-started-program/enroll")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("addToWorkoutPlan", false))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addedToWorkoutPlan").value(false))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andExpect(jsonPath("$.completedDaysCount").value(0))
                .andExpect(jsonPath("$.isCompleted").value(false))
                .andExpect(jsonPath("$.dayProgress", hasSize(3)))
                .andExpect(jsonPath("$.dayProgress[0].status").value("NOT_STARTED"));

        // Verify Workout Plan is preserved and NO Getting Started sessions were scheduled
        WorkoutPlan reloadedPlan = workoutPlanRepository.findById(personalPlan.getId()).orElseThrow();
        assertThat(reloadedPlan.isActive()).isTrue();
        assertThat(workoutSessionRepository.findAll()).isEmpty();

        mockMvc.perform(get("/api/v1/workout-sessions/active")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("localDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 2. Direct complete-day without completing a real session must be rejected
        mockMvc.perform(post("/api/v1/getting-started-program/complete-day/1")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isConflict());

        // 3. Starting Day 1 marks IN_PROGRESS only (NEVER COMPLETED on open)
        mockMvc.perform(post("/api/v1/getting-started-program/days/1/start")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("workoutDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/getting-started-program/enrollments")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].completedDaysCount").value(0))
                .andExpect(jsonPath("$[0].isCompleted").value(false))
                .andExpect(jsonPath("$[0].dayProgress[0].status").value("IN_PROGRESS"));

        // 4. Complete Days 1, 2, 3 via real workout completion
        startAndFinishDayWithAllSetsCompleted(1, today);
        mockMvc.perform(get("/api/v1/getting-started-program/enrollments")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].completedDaysCount").value(1))
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$[0].dayProgress[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].dayProgress[1].status").value("NOT_STARTED"));

        startAndFinishDayWithAllSetsCompleted(2, today.plusDays(1));
        startAndFinishDayWithAllSetsCompleted(3, today.plusDays(2));

        // 5. All 3 days complete -> enrollment transitions to COMPLETED
        mockMvc.perform(get("/api/v1/getting-started-program/enrollments")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].completedDaysCount").value(3))
                .andExpect(jsonPath("$[0].isCompleted").value(true))
                .andExpect(jsonPath("$[0].completedAt").isNotEmpty());

        // Personal plan remains untouched
        assertThat(workoutPlanRepository.findById(personalPlan.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void enrollYesAddToWorkoutPlan_conflictConfirmation_idempotency_partialRetry_andCompletedCleanup() throws Exception {
        LocalDate today = LocalDate.now();
        WorkoutDayOfWeek day1Dow = WorkoutDayOfWeek.valueOf(today.getDayOfWeek().name());
        WorkoutDayOfWeek day2Dow = WorkoutDayOfWeek.valueOf(today.plusDays(1).getDayOfWeek().name());

        WorkoutPlan personalPlan = createPersonalPlanForWeekday(day1Dow, "Personal PPL");
        WorkoutPlanAssignment coachAssignment = createCoachAssignmentForWeekday(day2Dow, "Coach Mastery Plan");

        // 1. Preview detects conflicts without mutating plans
        mockMvc.perform(get("/api/v1/getting-started-program/enroll/preview")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("startDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasConflicts").value(true))
                .andExpect(jsonPath("$.conflicts[0].existingWorkoutTitle").isNotEmpty());

        // 2. Enroll Yes WITHOUT confirming conflicts returns 409 SCHEDULE_CONFLICT
        mockMvc.perform(post("/api/v1/getting-started-program/enroll")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addToWorkoutPlan", true,
                                "confirmConflicts", false,
                                "startDate", today.toString()
                        ))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.conflicts[0].existingWorkoutTitle").isNotEmpty());

        assertThat(enrollmentRepository.count()).isZero();

        // 3. Enroll Yes WITH conflict confirmation succeeds & schedules 3 GS sessions
        MvcResult enrollResult = mockMvc.perform(post("/api/v1/getting-started-program/enroll")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addToWorkoutPlan", true,
                                "confirmConflicts", true,
                                "startDate", today.toString()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.addedToWorkoutPlan").value(true))
                .andExpect(jsonPath("$.status").value("NOT_STARTED"))
                .andReturn();
        long enrollmentId = objectMapper.readTree(enrollResult.getResponse().getContentAsString()).get("id").asLong();

        // Duplicate enroll is idempotent (no second enrollment or extra sessions)
        mockMvc.perform(post("/api/v1/getting-started-program/enroll")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "addToWorkoutPlan", true,
                                "confirmConflicts", true,
                                "startDate", today.toString()
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(enrollmentId));

        assertThat(enrollmentRepository.count()).isEqualTo(1);
        List<WorkoutSession> gsSessions = workoutSessionRepository.findAll().stream()
                .filter(s -> s.getSource() == WorkoutSessionSource.GETTING_STARTED)
                .toList();
        assertThat(gsSessions).hasSize(3);

        // Personal & Coach plans untouched
        assertThat(workoutPlanRepository.findById(personalPlan.getId()).orElseThrow().isActive()).isTrue();
        assertThat(assignmentRepository.findById(coachAssignment.getId()).orElseThrow().getStatus())
                .isEqualTo(WorkoutPlanAssignmentStatus.ACTIVE);

        // Today's active & upcoming endpoints expose Day 1 on active, Days 2 & 3 on upcoming
        mockMvc.perform(get("/api/v1/workout-sessions/active")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("localDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("GETTING_STARTED"))
                .andExpect(jsonPath("$[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$[0].gettingStartedDayNumber").value(1));

        mockMvc.perform(get("/api/v1/workout-sessions/upcoming")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("localDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 4. Partial workout completion on Day 1 must NOT mark Day 1 COMPLETED
        MvcResult partialStart = mockMvc.perform(post("/api/v1/getting-started-program/days/1/start")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("workoutDate", today.toString()))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode partialSession = objectMapper.readTree(partialStart.getResponse().getContentAsString());
        long partialSessionId = partialSession.get("id").asLong();
        JsonNode firstEx = partialSession.get("exercises").get(0);
        JsonNode firstSet = firstEx.get("sets").get(0);
        // Complete only 1 set out of multiple exercises -> leaves INCOMPLETE exercises
        mockMvc.perform(put("/api/v1/workout-sessions/{sessionId}/exercises/{exerciseId}/sets/{setId}",
                        partialSessionId, firstEx.get("id").asLong(), firstSet.get("id").asLong())
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new UpdateWorkoutSessionSetRequest(
                                8, null, null, null, true, null, firstSet.get("version").asLong()))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/workout-sessions/{sessionId}/finish", partialSessionId)
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new FinishWorkoutSessionRequest(true))))
                .andExpect(status().isOk());

        GettingStartedEnrollment afterPartial = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(afterPartial.getStatus()).isEqualTo(GettingStartedEnrollmentStatus.IN_PROGRESS);
        assertThat(dayProgressRepository.findByEnrollmentIdAndDayNumber(enrollmentId, 1).orElseThrow().getStatus())
                .isEqualTo(GettingStartedDayStatus.IN_PROGRESS);

        // 5. Athlete retries Day 1 cleanly & finishes Days 1, 2, 3 completely
        startAndFinishDayWithAllSetsCompleted(1, today);
        startAndFinishDayWithAllSetsCompleted(2, today.plusDays(1));
        startAndFinishDayWithAllSetsCompleted(3, today.plusDays(2));

        GettingStartedEnrollment completedEnrollment = enrollmentRepository.findById(enrollmentId).orElseThrow();
        assertThat(completedEnrollment.getStatus()).isEqualTo(GettingStartedEnrollmentStatus.COMPLETED);
        assertThat(completedEnrollment.getCompletedAt()).isNotNull();

        // 6. After completion: active & upcoming return zero GETTING_STARTED sessions, no recurring sessions exist,
        // and completed program + session history remain preserved!
        mockMvc.perform(get("/api/v1/workout-sessions/active")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("localDate", today.plusDays(3).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/workout-sessions/upcoming")
                        .header("Authorization", "Bearer " + athleteToken)
                        .param("localDate", today.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/v1/getting-started-program/enrollments")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$[0].isCompleted").value(true));

        mockMvc.perform(get("/api/v1/workout-sessions")
                        .header("Authorization", "Bearer " + athleteToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(4)));
    }
}
