package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.data.AdminConfirmationDecisionRequest;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecuteRequest;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.repository.AdminAiPendingActionRepository;
import com.calisthenics.backend.adminai.service.AdminToolRouter;
import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
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
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedDayRequest;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedExerciseRequest;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignEventRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedExerciseOperationRequest;
import com.calisthenics.backend.gettingstarted.dto.UpdateDismissalStatusRequest;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgram;
import com.calisthenics.backend.gettingstarted.enums.GettingStartedCampaignEventType;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedEnrollmentRepository;
import com.calisthenics.backend.gettingstarted.repository.GettingStartedProgramRepository;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionSource;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAiGettingStartedCampaignIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AdminToolRouter adminToolRouter;

    @Autowired private UserRepository userRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private CoachClientRelationshipRepository relationshipRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private WorkoutPlanAssignmentRepository assignmentRepository;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;

    @Autowired private GettingStartedProgramRepository programRepository;
    @Autowired private GettingStartedEnrollmentRepository enrollmentRepository;

    @Autowired private AdminAiPendingActionRepository pendingActionRepository;
    @Autowired private AdminAiAuditLogRepository auditLogRepository;

    @MockBean
    private GeminiApiClient geminiApiClient;

    private User adminUser;
    private User recentAthleteA;
    private User recentAthleteB;
    private User mediumAthlete;
    private User oldAthlete;
    private User coachUser;

    private WorkoutPlan coachPlan;
    private WorkoutPlanAssignment coachAssignment;
    private Exercise inclinePushUp;

    private String adminToken;
    private String athleteToken;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.callAdminAgent(anyString(), anyList())).thenReturn(Optional.empty());

        cleanDb();
        seedData();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanDb();
    }

    // Delete children before parents across all schema tables so shared H2 test state from
    // earlier non-transactional tests (e.g., workout_sessions -> workout_plan_assignments) never blocks cleanup.
    private static final List<String> TABLES_IN_DELETE_ORDER = List.of(
            "bulk_exercise_generation_items",
            "bulk_exercise_generation_jobs",
            "admin_ai_chat_idempotency",
            "admin_ai_pending_actions",
            "admin_ai_audit_logs",
            "admin_ai_recommendations",
            "admin_ai_messages",
            "admin_ai_conversations",
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

    private void cleanDb() {
        for (String table : TABLES_IN_DELETE_ORDER) {
            jdbcTemplate.update("DELETE FROM " + table);
        }
    }

    @org.junit.jupiter.api.AfterEach
    void tearDownGettingStartedCampaignTest() {
        cleanDb();
    }

    private void seedData() {
        adminUser = userRepository.save(User.builder()
                .email("admin.gs@calisthenics.test")
                .passwordHash("$2a$10$AdminHash")
                .fullName("Getting Started Admin")
                .role(Role.ROLE_ADMIN)
                .experienceLevel("ADVANCED")
                .build());

        recentAthleteA = userRepository.save(User.builder()
                .email("recent.a@calisthenics.test")
                .passwordHash("$2a$10$AthleteHashA")
                .fullName("Recent Athlete A")
                .role(Role.ROLE_USER)
                .experienceLevel("BEGINNER")
                .build());
        recentAthleteB = userRepository.save(User.builder()
                .email("recent.b@calisthenics.test")
                .passwordHash("$2a$10$AthleteHashB")
                .fullName("Recent Athlete B")
                .role(Role.ROLE_USER)
                .experienceLevel("BEGINNER")
                .build());
        mediumAthlete = userRepository.save(User.builder()
                .email("medium.athlete@calisthenics.test")
                .passwordHash("$2a$10$MediumHash")
                .fullName("Medium Athlete")
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build());
        oldAthlete = userRepository.save(User.builder()
                .email("old.athlete@calisthenics.test")
                .passwordHash("$2a$10$OldHash")
                .fullName("Old Athlete")
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build());
        coachUser = userRepository.save(User.builder()
                .email("coach.gs@calisthenics.test")
                .passwordHash("$2a$10$CoachHash")
                .fullName("Coach Lee")
                .role(Role.ROLE_USER)
                .experienceLevel("ELITE")
                .build());
        coachProfileRepository.save(CoachProfile.builder()
                .user(coachUser)
                .displayName("Coach Lee")
                .specialties("Calisthenics Basics")
                .yearsExperience(6)
                .isPublic(true)
                .isAcceptingClients(true)
                .isVerified(true)
                .build());

        // Set explicit account creation timestamps so audience counts are deterministic:
        // ALL_ATHLETES (ROLE_USER) = 5 (2 recent + 1 medium + 1 old + 1 coach)
        // LAST_30_DAYS = 3 (2 recent @ 3d + 1 medium @ 20d)
        // LAST_14_DAYS = 2 (2 recent @ 3d)
        updateUserCreatedAtDaysAgo(recentAthleteA.getId(), 3);
        updateUserCreatedAtDaysAgo(recentAthleteB.getId(), 5);
        updateUserCreatedAtDaysAgo(mediumAthlete.getId(), 20);
        updateUserCreatedAtDaysAgo(oldAthlete.getId(), 45);
        updateUserCreatedAtDaysAgo(coachUser.getId(), 60);

        // Seed beginner catalog exercises across categories
        exerciseRepository.save(Exercise.builder()
                .name("Standard Push-Up")
                .code("GS_STANDARD_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());
        inclinePushUp = exerciseRepository.save(Exercise.builder()
                .name("Incline Push-Up")
                .code("GS_INCLINE_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("Bench")
                .active(true)
                .build());
        exerciseRepository.save(Exercise.builder()
                .name("Knee Push-Up")
                .code("GS_KNEE_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());
        exerciseRepository.save(Exercise.builder()
                .name("Australian Pull-Up")
                .code("GS_AUSTRALIAN_PULL_UP")
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("Bar")
                .active(true)
                .build());
        exerciseRepository.save(Exercise.builder()
                .name("Bodyweight Squat")
                .code("GS_BODYWEIGHT_SQUAT")
                .category(ExerciseCategory.LEGS)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());
        exerciseRepository.save(Exercise.builder()
                .name("Forearm Plank")
                .code("GS_FOREARM_PLANK")
                .category(ExerciseCategory.ISOMETRIC)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());
        exerciseRepository.save(Exercise.builder()
                .name("Dead Bug")
                .code("GS_DEAD_BUG")
                .category(ExerciseCategory.CORE)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());

        // Existing coach-assigned workout plan that must NEVER be modified by Getting Started campaigns
        CoachClientRelationship relationship = relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coachUser)
                .client(mediumAthlete)
                .requestedBy(mediumAthlete)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());
        coachPlan = workoutPlanRepository.save(WorkoutPlan.builder()
                .user(coachUser)
                .name("Coach Hypertrophy Foundation")
                .splitType(WorkoutSplitType.CUSTOM)
                .active(true)
                .aiGenerated(false)
                .build());
        coachAssignment = assignmentRepository.save(WorkoutPlanAssignment.builder()
                .relationship(relationship)
                .workoutPlan(coachPlan)
                .coach(coachUser)
                .client(mediumAthlete)
                .status(WorkoutPlanAssignmentStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(7))
                .notes("Coach-assigned custom progression - do not alter")
                .build());

        adminToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(adminUser));
        athleteToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(recentAthleteA));
    }

    private void updateUserCreatedAtDaysAgo(Long userId, long daysAgo) {
        Timestamp ts = Timestamp.from(Instant.now().minus(daysAgo, ChronoUnit.DAYS));
        jdbcTemplate.update("UPDATE users SET created_at = ? WHERE id = ?", ts, userId);
    }

    @Test
    void fullAdminAiGettingStartedCampaignWorkflow_naturalLanguageEditsConfirmationAnalyticsAndUnpublish() throws Exception {
        // 1. Natural language: "Create a beginner-friendly 3-day full-body program."
        MvcResult createResult = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Create a beginner-friendly 3-day full-body program.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedTools[*].toolName", hasItem("createProgram")))
                .andExpect(jsonPath("$.gettingStartedProgram.id").isNumber())
                .andExpect(jsonPath("$.gettingStartedProgram.published").value(false))
                .andExpect(jsonPath("$.gettingStartedProgram.days", hasSize(3)))
                .andReturn();

        JsonNode createJson = objectMapper.readTree(createResult.getResponse().getContentAsString());
        Long conversationId = createJson.get("conversationId").asLong();
        Long programId = createJson.get("gettingStartedProgram").get("id").asLong();

        assertThat(enrollmentRepository.count()).isZero();

        // 2. Natural language edit: "Make Day 1 focused on pushing movements."
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message("Make Day 1 focused on pushing movements.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedTools[*].toolName", hasItem("updateProgram")))
                .andExpect(jsonPath("$.gettingStartedProgram.id").value(programId))
                .andExpect(jsonPath("$.gettingStartedProgram.published").value(false))
                .andExpect(jsonPath("$.gettingStartedProgram.days[0].title", containsStringIgnoringCase("Push")));

        // 3. Structured updateProgram operations: add, update sets/reps/rest/instructions, reorder, remove
        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("updateProgram")
                                .programId(programId)
                                .conversationId(conversationId)
                                .operations(List.of(
                                        GettingStartedExerciseOperationRequest.builder()
                                                .operation("ADD_EXERCISE")
                                                .dayNumber(2)
                                                .exerciseId(inclinePushUp.getId())
                                                .exerciseName("Incline Push-Up")
                                                .sets(3)
                                                .reps(12)
                                                .restSeconds(45)
                                                .instructions("Keep core braced on bench incline.")
                                                .build(),
                                        GettingStartedExerciseOperationRequest.builder()
                                                .operation("UPDATE_EXERCISE")
                                                .dayNumber(2)
                                                .targetExerciseName("Incline Push-Up")
                                                .sets(4)
                                                .reps(10)
                                                .restSeconds(75)
                                                .instructions("Lower slowly for 2 seconds.")
                                                .build()
                                ))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.status").value("EXECUTED"))
                .andExpect(jsonPath("$.data.program.days[1].exercises[?(@.exerciseName=='Incline Push-Up')].sets", hasItem(4)))
                .andExpect(jsonPath("$.data.program.days[1].exercises[?(@.exerciseName=='Incline Push-Up')].reps", hasItem(10)))
                .andExpect(jsonPath("$.data.program.days[1].exercises[?(@.exerciseName=='Incline Push-Up')].restSeconds", hasItem(75)));

        // Reorder and remove Incline Push-Up via updateProgram
        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("updateProgram")
                                .programId(programId)
                                .operations(List.of(
                                        GettingStartedExerciseOperationRequest.builder()
                                                .operation("REORDER_EXERCISES")
                                                .dayNumber(2)
                                                .targetExerciseName("Incline Push-Up")
                                                .newOrder(0)
                                                .build()
                                ))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.program.days[1].exercises[0].exerciseName").value("Incline Push-Up"));

        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("updateProgram")
                                .programId(programId)
                                .operations(List.of(
                                        GettingStartedExerciseOperationRequest.builder()
                                                .operation("REMOVE_EXERCISE")
                                                .dayNumber(2)
                                                .targetExerciseName("Incline Push-Up")
                                                .build()
                                ))
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.program.days[1].exercises[?(@.exerciseName=='Incline Push-Up')]", empty()));

        // 4. previewProgram & eligibility sizing
        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("previewProgram")
                                .programId(programId)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preview.valid").value(true))
                .andExpect(jsonPath("$.data.preview.validationErrors", empty()))
                .andExpect(jsonPath("$.data.preview.eligibleAthleteCount").value(5));

        // 5. Natural language: "Change the target audience to all athletes."
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message("Change the target audience to all athletes.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedTools[*].toolName", hasItem("configureAudience")))
                .andExpect(jsonPath("$.gettingStartedProgram.targetAudience").value("ALL_ATHLETES"));

        // 6. Natural language publish with audience: MUST require confirmation first (stays unpublished!)
        MvcResult publishPromptResult = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message("Publish this program for users who joined within the last 14 days.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedTools[*].status", hasItem("CONFIRMATION_REQUIRED")))
                .andExpect(jsonPath("$.pendingConfirmation.actionType").value("PUBLISH_PROGRAM"))
                .andExpect(jsonPath("$.pendingConfirmation.targetAudience").value("LAST_14_DAYS"))
                .andExpect(jsonPath("$.pendingConfirmation.eligibleAthleteCount").value(2))
                .andExpect(jsonPath("$.gettingStartedProgram.published").value(false))
                .andReturn();

        JsonNode pendingNode = objectMapper.readTree(publishPromptResult.getResponse().getContentAsString())
                .get("pendingConfirmation");
        String publishToken = pendingNode.get("confirmationToken").asText();
        assertThat(publishToken).isNotBlank();

        // Still unpublished in DB before confirmation!
        GettingStartedProgram preConfirmDb = programRepository.findById(programId).orElseThrow();
        assertThat(preConfirmDb.isPublished()).isFalse();

        // 7. Confirm publish via natural language explicit confirmation turn
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message("Yes, confirm publish.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConfirmation").doesNotExist())
                .andExpect(jsonPath("$.gettingStartedProgram.published").value(true))
                .andExpect(jsonPath("$.gettingStartedProgram.targetAudience").value("LAST_14_DAYS"))
                .andExpect(jsonPath("$.activeCampaigns", hasSize(1)));

        GettingStartedProgram publishedDb = programRepository.findById(programId).orElseThrow();
        assertThat(publishedDb.isPublished()).isTrue();
        assertThat(publishedDb.getTargetAudience()).isEqualTo(TargetAudience.LAST_14_DAYS);

        // Safety: publishing must NEVER auto-enroll athletes and must NEVER modify coach plans
        assertThat(enrollmentRepository.count()).isZero();
        WorkoutPlan untouchedCoachPlan = workoutPlanRepository.findById(coachPlan.getId()).orElseThrow();
        WorkoutPlanAssignment untouchedAssignment = assignmentRepository.findById(coachAssignment.getId()).orElseThrow();
        assertThat(untouchedCoachPlan.getName()).isEqualTo("Coach Hypertrophy Foundation");
        assertThat(untouchedAssignment.getStatus()).isEqualTo(WorkoutPlanAssignmentStatus.ACTIVE);
        assertThat(untouchedAssignment.getNotes()).isEqualTo("Coach-assigned custom progression - do not alter");

        // 8. Natural language: "Show me the currently active Getting Started campaign."
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message("Show me the currently active Getting Started campaign.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedTools[*].toolName", hasItem("getActiveCampaigns")))
                .andExpect(jsonPath("$.activeCampaigns", hasSize(1)))
                .andExpect(jsonPath("$.activeCampaigns[0].programId").value(programId))
                .andExpect(jsonPath("$.activeCampaigns[0].eligibleAthleteCount").value(2));

        // 9. Simulate athlete engagement telemetry & completion across both eligible recent athletes
        String athleteBToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(recentAthleteB));

        // Athlete A: modal impression, detail view, dismiss banner, enroll, complete all 3 days
        recordAthleteEngagement(athleteToken, programId, true, 3);
        // Athlete B: modal impression, detail view, enroll, complete Day 1 only (no dismissal)
        recordAthleteEngagement(athleteBToken, programId, false, 1);

        // 10. Query campaign analytics via Admin AI Chat
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message("Show campaign analytics and completion rates for Getting Started.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedTools[*].toolName", hasItem("getCampaignAnalytics")))
                .andExpect(jsonPath("$.campaignAnalytics.eligibleAthleteCount").value(2))
                .andExpect(jsonPath("$.campaignAnalytics.modalImpressions").value(2))
                .andExpect(jsonPath("$.campaignAnalytics.modalDismissals").value(1))
                .andExpect(jsonPath("$.campaignAnalytics.programDetailViews").value(2))
                .andExpect(jsonPath("$.campaignAnalytics.enrollments").value(2))
                .andExpect(jsonPath("$.campaignAnalytics.day1CompletionRate").value(100.0))
                .andExpect(jsonPath("$.campaignAnalytics.day2CompletionRate").value(50.0))
                .andExpect(jsonPath("$.campaignAnalytics.day3CompletionRate").value(50.0))
                .andExpect(jsonPath("$.campaignAnalytics.overallCompletionRate").value(50.0));

        // 11. Natural language: "Unpublish the current campaign." -> requires confirmation before unpublishing
        MvcResult unpublishPromptResult = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(conversationId)
                                .message("Unpublish the current campaign.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.executedTools[*].status", hasItem("CONFIRMATION_REQUIRED")))
                .andExpect(jsonPath("$.pendingConfirmation.actionType").value("UNPUBLISH_PROGRAM"))
                .andReturn();

        String unpublishToken = objectMapper.readTree(unpublishPromptResult.getResponse().getContentAsString())
                .get("pendingConfirmation").get("confirmationToken").asText();

        // Still published prior to explicit confirmation
        assertThat(programRepository.findById(programId).orElseThrow().isPublished()).isTrue();

        // Confirm unpublish via confirmation endpoint
        mockMvc.perform(post("/api/v1/admin/ai/confirmations/" + unpublishToken)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminConfirmationDecisionRequest.builder()
                                .approved(true)
                                .conversationId(conversationId)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.execution.status").value("EXECUTED"))
                .andExpect(jsonPath("$.gettingStartedProgram.published").value(false))
                .andExpect(jsonPath("$.activeCampaigns", empty()));

        assertThat(programRepository.findById(programId).orElseThrow().isPublished()).isFalse();
        assertThat(auditLogRepository.findTop50ByOrderByCreatedAtDesc())
                .extracting("action")
                .contains("TOOL_CREATE_PROGRAM", "TOOL_UPDATE_PROGRAM", "TOOL_PREVIEW_PROGRAM",
                        "TOOL_CONFIGURE_AUDIENCE", "TOOL_PUBLISH_PROGRAM", "TOOL_GET_ACTIVE_CAMPAIGNS",
                        "TOOL_GET_CAMPAIGN_ANALYTICS", "TOOL_UNPUBLISH_PROGRAM");
    }

    private void recordAthleteEngagement(String token, Long programId, boolean dismissBanner, int completedDays) throws Exception {
        mockMvc.perform(post("/api/v1/getting-started-program/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GettingStartedCampaignEventRequest.builder()
                                .eventType(GettingStartedCampaignEventType.MODAL_IMPRESSION)
                                .programId(programId)
                                .build())))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/getting-started-program/events")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(GettingStartedCampaignEventRequest.builder()
                                .eventType(GettingStartedCampaignEventType.PROGRAM_DETAIL_VIEW)
                                .programId(programId)
                                .build())))
                .andExpect(status().isNoContent());

        if (dismissBanner) {
            mockMvc.perform(patch("/api/v1/getting-started-program/dismissal-status")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(UpdateDismissalStatusRequest.builder()
                                    .status(OnboardingStatus.COMPACT_BANNER)
                                    .build())))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post("/api/v1/getting-started-program/enroll")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        for (int day = 1; day <= completedDays; day++) {
            MvcResult startRes = mockMvc.perform(post("/api/v1/getting-started-program/days/" + day + "/start")
                            .header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode sessionNode = objectMapper.readTree(startRes.getResponse().getContentAsString());
            long sessionId = sessionNode.get("id").asLong();
            for (JsonNode exNode : sessionNode.get("exercises")) {
                long exId = exNode.get("id").asLong();
                for (JsonNode setNode : exNode.get("sets")) {
                    long setId = setNode.get("id").asLong();
                    long setVersion = setNode.get("version").asLong();
                    Integer reps = setNode.hasNonNull("reps") ? setNode.get("reps").asInt() : 10;
                    String hold = setNode.hasNonNull("holdDurationSeconds")
                            ? String.valueOf(setNode.get("holdDurationSeconds").asInt())
                            : "null";
                    String setBody = String.format(
                            "{\"reps\":%d,\"holdDurationSeconds\":%s,\"completed\":true,\"expectedVersion\":%d}",
                            reps, hold, setVersion);
                    mockMvc.perform(put("/api/v1/workout-sessions/" + sessionId + "/exercises/" + exId + "/sets/" + setId)
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(setBody))
                            .andExpect(status().isOk());
                }
            }
            mockMvc.perform(post("/api/v1/workout-sessions/" + sessionId + "/finish")
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"confirmPartial\":false}"))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void validationSafetyAndNegationHandling_rejectsInvalidWorkoutDataAndRespectsCancellation() throws Exception {
        // Invalid workout data (only 1 day and 0 sets) must be rejected before saving
        AdminGettingStartedProgramRequest invalidProgram = AdminGettingStartedProgramRequest.builder()
                .title("Broken Draft")
                .targetAudience(TargetAudience.ALL_ATHLETES)
                .days(List.of(AdminGettingStartedDayRequest.builder()
                        .dayNumber(1)
                        .title("Only Day")
                        .exercises(List.of(AdminGettingStartedExerciseRequest.builder()
                                .exerciseName("Push-Up")
                                .sets(0)
                                .reps(0)
                                .build()))
                        .build()))
                .build();

        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("createProgram")
                                .program(invalidProgram)
                                .build())))
                .andExpect(status().isBadRequest());
        assertThat(programRepository.count()).isZero();

        // Create a valid draft, request publish, then send negation phrase -> must CANCEL, NOT publish!
        MvcResult draftResult = mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("createProgram")
                                .instruction("Create beginner full body program")
                                .build())))
                .andExpect(status().isOk())
                .andReturn();
        Long draftId = objectMapper.readTree(draftResult.getResponse().getContentAsString())
                .get("data").get("program").get("id").asLong();

        MvcResult chatConfirm = mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Publish program " + draftId)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConfirmation.confirmationToken").isNotEmpty())
                .andReturn();

        JsonNode confirmJson = objectMapper.readTree(chatConfirm.getResponse().getContentAsString());
        Long convId = confirmJson.get("conversationId").asLong();
        String token = confirmJson.get("pendingConfirmation").get("confirmationToken").asText();

        // Direct publish attempt with confirmed=true without valid confirmation token fails
        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("publishProgram")
                                .programId(draftId)
                                .confirmed(true)
                                .confirmationToken("forged-token-xyz")
                                .build())))
                .andExpect(status().isBadRequest());

        // Negated chat reply ("No, do not confirm publish") must cancel pending action instead of publishing
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .conversationId(convId)
                                .message("No, do not confirm publish yet.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingConfirmation").doesNotExist());

        assertThat(programRepository.findById(draftId).orElseThrow().isPublished()).isFalse();
        assertThat(pendingActionRepository.findByConfirmationTokenAndAdminUserId(token, adminUser.getId())
                .orElseThrow().getStatus()).isEqualTo(AdminPendingActionStatus.CANCELLED);
    }

    @Test
    void rbacEnforcement_athleteAndUnauthenticatedUsersCannotInvokeAdminGettingStartedOperations() throws Exception {
        // Unauthenticated requests are rejected with 401
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Create a 3-day Getting Started program\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"toolName\":\"createProgram\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/admin/ai/confirmations/some-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isUnauthorized());

        // Athlete (ROLE_USER) Bearer token is forbidden (403) across HTTP endpoints
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Publish Getting Started program for all athletes\"}"))
                .andExpect(status().isForbidden());

        for (AdminToolName gsTool : List.of(
                AdminToolName.CREATE_PROGRAM,
                AdminToolName.UPDATE_PROGRAM,
                AdminToolName.PREVIEW_PROGRAM,
                AdminToolName.CONFIGURE_AUDIENCE,
                AdminToolName.PUBLISH_PROGRAM,
                AdminToolName.UNPUBLISH_PROGRAM,
                AdminToolName.GET_ACTIVE_CAMPAIGNS,
                AdminToolName.GET_CAMPAIGN_ANALYTICS
        )) {
            mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                            .header("Authorization", "Bearer " + athleteToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                    .toolName(gsTool.getToolName())
                                    .build())))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(post("/api/v1/admin/ai/confirmations/any-token")
                        .header("Authorization", "Bearer " + athleteToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());

        // Direct service/router invocation under athlete SecurityContext throws AccessDeniedException
        UserPrincipal athletePrincipal = UserPrincipal.create(recentAthleteA);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(athletePrincipal, null, athletePrincipal.getAuthorities()));

        assertThrows(AccessDeniedException.class, () ->
                adminToolRouter.executeSingleTool(AdminToolExecuteRequest.builder()
                        .toolName(AdminToolName.CREATE_PROGRAM.getToolName())
                        .instruction("Create program")
                        .build()));
    }

    @Test
    void cleanDb_deletesDependentWorkoutSessionsBeforeAssignments() {
        workoutSessionRepository.save(WorkoutSession.builder()
                .user(recentAthleteA)
                .workoutPlan(coachPlan)
                .workoutPlanAssignment(coachAssignment)
                .title("Assigned Coach Session")
                .source(WorkoutSessionSource.COACH)
                .status(WorkoutSessionStatus.COMPLETED)
                .workoutDate(LocalDate.now())
                .build());

        assertThat(workoutSessionRepository.count()).isPositive();
        cleanDb();

        assertThat(workoutSessionRepository.count()).isZero();
        assertThat(assignmentRepository.count()).isZero();
        assertThat(workoutPlanRepository.count()).isZero();
        assertThat(userRepository.count()).isZero();
    }
}
