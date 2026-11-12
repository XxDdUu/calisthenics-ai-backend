package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.*;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.repository.AdminAiConversationRepository;
import com.calisthenics.backend.adminai.repository.AdminAiMessageRepository;
import com.calisthenics.backend.adminai.repository.AdminAiRecommendationRepository;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.*;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminAiIntegrationAndSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CoachProfileRepository coachProfileRepository;

    @Autowired
    private ClientProfileRepository clientProfileRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private WorkoutPlanAssignmentRepository assignmentRepository;

    @Autowired
    private ExerciseRepository exerciseRepository;

    @Autowired
    private WorkoutPlanRepository workoutPlanRepository;

    @Autowired
    private WorkoutSessionRepository workoutSessionRepository;

    @Autowired
    private WorkoutSessionExerciseRepository workoutSessionExerciseRepository;

    @Autowired
    private PersonalRecordRepository personalRecordRepository;

    @Autowired
    private AiConversationRepository userAiConversationRepository;

    @Autowired
    private AdminAiConversationRepository adminAiConversationRepository;

    @Autowired
    private AdminAiMessageRepository adminAiMessageRepository;

    @Autowired
    private AdminAiRecommendationRepository adminAiRecommendationRepository;

    @Autowired
    private AdminAiAuditLogRepository adminAiAuditLogRepository;

    @Autowired
    private ClientBodyMeasurementRepository clientBodyMeasurementRepository;

    private User adminUser;
    private User coachUser;
    private User clientUser;
    private Exercise existingTuckPlanche;
    private Exercise existingPullUp;

    private void cleanDb() {
        adminAiAuditLogRepository.deleteAll();
        adminAiRecommendationRepository.deleteAll();
        adminAiMessageRepository.deleteAll();
        adminAiConversationRepository.deleteAll();
        userAiConversationRepository.deleteAll();
        personalRecordRepository.deleteAll();
        workoutSessionExerciseRepository.deleteAll();
        workoutSessionRepository.deleteAll();
        assignmentRepository.deleteAll();
        workoutPlanRepository.deleteAll();
        relationshipRepository.deleteAll();
        clientBodyMeasurementRepository.deleteAll();
        clientProfileRepository.deleteAll();
        coachProfileRepository.deleteAll();
        exerciseRepository.deleteAll();
        userRepository.deleteAll();
    }

    @AfterEach
    void tearDown() {
        cleanDb();
    }

    @BeforeEach
    void setUp() {
        cleanDb();

        adminUser = userRepository.save(User.builder()
                .email("admin@calisthenics.ai")
                .fullName("Platform Admin")
                .role(Role.ROLE_ADMIN)
                .build());

        coachUser = userRepository.save(User.builder()
                .email("coach@calisthenics.ai")
                .fullName("Coach Alex")
                .role(Role.ROLE_USER)
                .build());

        clientUser = userRepository.save(User.builder()
                .email("athlete@calisthenics.ai")
                .fullName("Athlete Sam")
                .role(Role.ROLE_USER)
                .build());

        coachProfileRepository.save(CoachProfile.builder()
                .user(coachUser)
                .displayName("Coach Alex")
                .specialties("Planche, Front Lever")
                .build());

        clientProfileRepository.save(ClientProfile.builder()
                .user(clientUser)
                .displayName("Athlete Sam")
                .experienceLevel("INTERMEDIATE")
                .primaryGoals("Planche, Front Lever")
                .trainingInterests("Handstand, Muscle-up")
                .build());

        relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coachUser)
                .client(clientUser)
                .requestedBy(clientUser)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .version(0L)
                .build());

        existingTuckPlanche = exerciseRepository.save(Exercise.builder()
                .name("Tuck Planche")
                .code("TUCK_PLANCHE")
                .category(ExerciseCategory.SKILL)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .description("Beginner planche progression with knees tucked into chest.")
                .targetMuscles("Shoulders, Core")
                .instructions("Lock arms and protract scapula.")
                .active(true)
                .build());

        existingPullUp = exerciseRepository.save(Exercise.builder()
                .name("Pull-Up")
                .code("PULL_UP")
                .category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .description("Vertical pull-up.")
                .targetMuscles("Lats, Biceps")
                .instructions("Pull chin above bar.")
                .active(true)
                .build());

        WorkoutPlan clientPlan = workoutPlanRepository.save(WorkoutPlan.builder()
                .user(clientUser)
                .name("Sam's Planche Routine")
                .splitType(WorkoutSplitType.UPPER_LOWER)
                .active(true)
                .aiGenerated(false)
                .build());

        WorkoutSession session = workoutSessionRepository.save(WorkoutSession.builder()
                .user(clientUser)
                .workoutPlan(clientPlan)
                .title("Planche & Pull Session")
                .workoutDate(LocalDate.now())
                .status(WorkoutSessionStatus.COMPLETED)
                .completedAt(ZonedDateTime.now())
                .build());

        workoutSessionExerciseRepository.save(WorkoutSessionExercise.builder()
                .workoutSession(session)
                .exercise(existingTuckPlanche)
                .exerciseNameSnapshot(existingTuckPlanche.getName())
                .orderIndex(0)
                .targetSets(4)
                .targetHoldDurationSeconds(12)
                .restSeconds(90)
                .status(WorkoutSessionExerciseStatus.COMPLETED)
                .build());

        personalRecordRepository.save(PersonalRecord.builder()
                .user(clientUser)
                .exercise(existingTuckPlanche)
                .recordType(PersonalRecordType.MAX_HOLD)
                .recordValue(BigDecimal.valueOf(15))
                .unit("s")
                .achievedAt(ZonedDateTime.now())
                .build());
    }

    private UsernamePasswordAuthenticationToken adminAuth() {
        UserPrincipal principal = UserPrincipal.create(adminUser);
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    void unauthenticatedRequestsToAdminAiAreRejected() throws Exception {
        mockMvc.perform(get("/api/admin/ai/analytics/overview"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Summarize platform activity\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(authorities = "ROLE_USER")
    void regularUserCannotAccessAdminAiEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/ai/analytics/overview"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/admin/ai/recommendations"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/ai/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Summarize platform activity\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(authorities = "ROLE_COACH")
    void coachCannotAccessAdminAiEndpoints() throws Exception {
        mockMvc.perform(get("/api/admin/ai/analytics/training"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/admin/ai/recommendations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":\"EXERCISE\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void platformAnalyticsReflectsActualDatabaseData() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/ai/analytics/overview")
                        .param("range", "LAST_30_DAYS")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andReturn();

        AdminPlatformOverviewResponse overview = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AdminPlatformOverviewResponse.class
        );

        assertThat(overview.getRange()).isEqualTo(AdminAnalyticsRange.LAST_30_DAYS);
        assertThat(overview.getTotalRegisteredUsers()).isEqualTo(3L);
        assertThat(overview.getTotalCoaches()).isEqualTo(1L);
        assertThat(overview.getTotalClients()).isEqualTo(1L);
        assertThat(overview.getTotalAdmins()).isEqualTo(1L);
        assertThat(overview.getActiveCoachClientRelationships()).isEqualTo(1L);
        assertThat(overview.getTotalWorkoutPlans()).isEqualTo(1L);
        assertThat(overview.getTotalCompletedWorkoutsAllTime()).isEqualTo(1L);
        assertThat(overview.getCompletedWorkoutsInPeriod()).isEqualTo(1L);
        assertThat(overview.getTotalRecordedPrsAllTime()).isEqualTo(1L);
        assertThat(overview.getRecordedPrsInPeriod()).isEqualTo(1L);
        assertThat(overview.getTotalExercises()).isEqualTo(2L);
        assertThat(overview.getActiveUsersInPeriod()).isEqualTo(1L);
    }

    @Test
    void trainingAnalyticsProducesAccurateAggregations() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/ai/analytics/training")
                        .param("range", "TODAY")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andReturn();

        AdminTrainingAnalyticsResponse training = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AdminTrainingAnalyticsResponse.class
        );

        assertThat(training.getMostPerformedExercises()).isNotEmpty();
        assertThat(training.getMostPerformedExercises().get(0).getExerciseName()).isEqualTo("Tuck Planche");
        assertThat(training.getMostPerformedExercises().get(0).getSessionPerformances()).isEqualTo(1L);
        assertThat(training.getMostPerformedExercises().get(0).getCompletionRatePercent()).isEqualTo(100.0);
        assertThat(training.getCommonTrainingGoals()).isNotEmpty();
        assertThat(training.getSkillFocusBreakdown()).anyMatch(s -> s.getSkillName().equals("Planche") && s.getExistingExerciseCount() == 1L);
    }

    @Test
    void exerciseRecommendationEngineExcludesExistingExercisesAndApprovesToCreateExercise() throws Exception {
        AdminChatRequest chatReq = AdminChatRequest.builder()
                .message("What exercises should we add to improve our Planche training library?")
                .build();

        MvcResult chatResult = mockMvc.perform(post("/api/admin/ai/chat")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isOk())
                .andReturn();

        AdminChatResponse chatResp = objectMapper.readValue(
                chatResult.getResponse().getContentAsString(),
                AdminChatResponse.class
        );

        assertThat(chatResp.getIntent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(chatResp.getRecommendations()).isNotEmpty();

        // Must NOT suggest "Tuck Planche" because it already exists in ExerciseRepository!
        List<String> recommendedExerciseNames = chatResp.getRecommendations().stream()
                .map(r -> r.getExerciseDetails().getName())
                .toList();
        assertThat(recommendedExerciseNames).doesNotContain("Tuck Planche");
        assertThat(recommendedExerciseNames).contains("Planche Lean");

        AdminRecommendationResponse firstRec = chatResp.getRecommendations().get(0);
        assertThat(firstRec.getStatus()).isEqualTo(AdminRecommendationStatus.PENDING_APPROVAL);

        // Approve the recommendation without auto-publishing
        MvcResult approveResult = mockMvc.perform(patch("/api/admin/ai/recommendations/" + firstRec.getId() + "/approve")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewNotes\":\"Great progression addition\",\"publishExercise\":false}"))
                .andExpect(status().isOk())
                .andReturn();

        AdminRecommendationResponse approved = objectMapper.readValue(
                approveResult.getResponse().getContentAsString(),
                AdminRecommendationResponse.class
        );

        assertThat(approved.getStatus()).isEqualTo(AdminRecommendationStatus.APPROVED);
        assertThat(approved.getCreatedResourceId()).isNotNull();
        assertThat(approved.getCreatedResourceType()).isEqualTo("EXERCISE");

        Exercise createdExercise = exerciseRepository.findById(approved.getCreatedResourceId()).orElseThrow();
        assertThat(createdExercise.getName()).isEqualTo(firstRec.getExerciseDetails().getName());
        assertThat(createdExercise.isActive()).isFalse(); // Not published until confirmed!

        // Confirm publication -> transitions to IMPLEMENTED and activates exercise
        MvcResult implementResult = mockMvc.perform(patch("/api/admin/ai/recommendations/" + firstRec.getId() + "/implement")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andReturn();

        AdminRecommendationResponse implemented = objectMapper.readValue(
                implementResult.getResponse().getContentAsString(),
                AdminRecommendationResponse.class
        );
        assertThat(implemented.getStatus()).isEqualTo(AdminRecommendationStatus.IMPLEMENTED);
        assertThat(exerciseRepository.findById(approved.getCreatedResourceId()).orElseThrow().isActive()).isTrue();
        assertThat(adminAiAuditLogRepository.count()).isGreaterThanOrEqualTo(2L);
    }

    @Test
    void workoutProgramRecommendationGeneratesDraftAndApprovesWithoutAutoPublishingOrAssigning() throws Exception {
        CreateAdminRecommendationRequest genReq = CreateAdminRecommendationRequest.builder()
                .type(AdminRecommendationType.WORKOUT_PROGRAM)
                .targetSkill("Planche")
                .trainingLevel("BEGINNER")
                .build();

        MvcResult createResult = mockMvc.perform(post("/api/admin/ai/recommendations")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(genReq)))
                .andExpect(status().isCreated())
                .andReturn();

        AdminRecommendationResponse[] created = objectMapper.readValue(
                createResult.getResponse().getContentAsString(),
                AdminRecommendationResponse[].class
        );

        assertThat(created).isNotEmpty();
        AdminRecommendationResponse draftProgramRec = created[0];
        assertThat(draftProgramRec.getType()).isEqualTo(AdminRecommendationType.WORKOUT_PROGRAM);
        assertThat(draftProgramRec.getStatus()).isEqualTo(AdminRecommendationStatus.DRAFT);
        assertThat(draftProgramRec.getProgramDetails()).isNotNull();
        assertThat(draftProgramRec.getProgramDetails().getProgramName()).contains("Planche");
        assertThat(draftProgramRec.getProgramDetails().getSafetyConsiderations()).isNotBlank();
        assertThat(draftProgramRec.getProgramDetails().getProgressionCriteria()).isNotBlank();

        long plansBefore = workoutPlanRepository.count();
        long assignmentsBefore = assignmentRepository.count();

        // Approve the draft program
        MvcResult approveResult = mockMvc.perform(patch("/api/admin/ai/recommendations/" + draftProgramRec.getId() + "/approve")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewNotes\":\"Approved as draft template\"}"))
                .andExpect(status().isOk())
                .andReturn();

        AdminRecommendationResponse approved = objectMapper.readValue(
                approveResult.getResponse().getContentAsString(),
                AdminRecommendationResponse.class
        );

        assertThat(approved.getStatus()).isEqualTo(AdminRecommendationStatus.APPROVED);
        assertThat(approved.getCreatedResourceId()).isNotNull();
        assertThat(approved.getCreatedResourceType()).isEqualTo("WORKOUT_PLAN");
        assertThat(workoutPlanRepository.count()).isEqualTo(plansBefore + 1);

        WorkoutPlan createdPlan = workoutPlanRepository.findById(approved.getCreatedResourceId()).orElseThrow();
        assertThat(createdPlan.isActive()).isFalse(); // Never auto-published
        assertThat(assignmentRepository.count()).isEqualTo(assignmentsBefore); // Never auto-assigned to users

        // Test rejecting another recommendation
        CreateAdminRecommendationRequest opReq = CreateAdminRecommendationRequest.builder()
                .type(AdminRecommendationType.OPERATIONAL)
                .build();
        MvcResult opResult = mockMvc.perform(post("/api/admin/ai/recommendations")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(opReq)))
                .andExpect(status().isCreated())
                .andReturn();
        AdminRecommendationResponse[] opRecs = objectMapper.readValue(
                opResult.getResponse().getContentAsString(),
                AdminRecommendationResponse[].class
        );
        assertThat(opRecs).isNotEmpty();

        mockMvc.perform(patch("/api/admin/ai/recommendations/" + opRecs[0].getId() + "/reject")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reviewNotes\":\"Deferred to next sprint\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.reviewNotes").value("Deferred to next sprint"));
    }

    @Test
    void adminConversationPersistsAndRemainsCompletelySeparateFromUserAiAssistant() throws Exception {
        // Seed a User AI Assistant conversation for clientUser
        userAiConversationRepository.save(AiConversation.builder()
                .user(clientUser)
                .title("Athlete Personal AI Chat")
                .build());

        // Create an Admin AI conversation and send a message
        MvcResult convResult = mockMvc.perform(post("/api/admin/ai/conversations")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Monthly Operations Audit\"}"))
                .andExpect(status().isCreated())
                .andReturn();

        AdminConversationSummaryResponse convSummary = objectMapper.readValue(
                convResult.getResponse().getContentAsString(),
                AdminConversationSummaryResponse.class
        );

        AdminChatRequest chatReq = AdminChatRequest.builder()
                .conversationId(convSummary.getId())
                .message("Summarize platform activity this month")
                .build();

        mockMvc.perform(post("/api/admin/ai/chat")
                        .with(authentication(adminAuth()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conversationId").value(convSummary.getId()))
                .andExpect(jsonPath("$.intent").value("PLATFORM_OVERVIEW"));

        // Verify messages persist in Admin AI conversation
        mockMvc.perform(get("/api/admin/ai/conversations/" + convSummary.getId() + "/messages")
                        .with(authentication(adminAuth())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].role").value("assistant"));

        // Verify Admin AI conversations and User AI conversations are completely separate
        assertThat(adminAiConversationRepository.count()).isEqualTo(1L);
        assertThat(userAiConversationRepository.count()).isEqualTo(1L);
        assertThat(adminAiConversationRepository.findAll().get(0).getTitle()).isEqualTo("Monthly Operations Audit");
        assertThat(userAiConversationRepository.findAll().get(0).getTitle()).isEqualTo("Athlete Personal AI Chat");
    }
}
