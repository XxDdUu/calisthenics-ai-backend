package com.calisthenics.backend.adminai;

import com.calisthenics.backend.adminai.dto.AdminChatRequest;
import com.calisthenics.backend.adminai.dto.data.*;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.repository.AdminAiConversationRepository;
import com.calisthenics.backend.adminai.repository.AdminAiMessageRepository;
import com.calisthenics.backend.adminai.repository.AdminAiRecommendationRepository;
import com.calisthenics.backend.adminai.service.AdminDataAccessService;
import com.calisthenics.backend.adminai.service.AdminToolRouter;
import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiRequestDto;
import com.calisthenics.backend.analytics.model.PersonalRecord;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.model.SharedPersonalRecord;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.repository.SharedPersonalRecordRepository;
import com.calisthenics.backend.bodymetrics.entity.ClientBodyMeasurement;
import com.calisthenics.backend.bodymetrics.repository.ClientBodyMeasurementRepository;
import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.entity.ChatMessage;
import com.calisthenics.backend.chat.presence.UserPresenceRecord;
import com.calisthenics.backend.chat.presence.UserPresenceRepository;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.chat.repository.ChatMessageRepository;
import com.calisthenics.backend.chat.type.ChatMessageType;
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
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.*;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminGlobalDataAndToolRouterTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private EntityManagerFactory entityManagerFactory;

    @Autowired private AdminDataAccessService adminDataAccessService;
    @Autowired private AdminToolRouter adminToolRouter;

    @Autowired private UserRepository userRepository;
    @Autowired private ClientProfileRepository clientProfileRepository;
    @Autowired private CoachProfileRepository coachProfileRepository;
    @Autowired private ClientBodyMeasurementRepository clientBodyMeasurementRepository;
    @Autowired private ExerciseRepository exerciseRepository;
    @Autowired private WorkoutPlanRepository workoutPlanRepository;
    @Autowired private CoachClientRelationshipRepository relationshipRepository;
    @Autowired private WorkoutPlanAssignmentRepository assignmentRepository;
    @Autowired private WorkoutSessionRepository workoutSessionRepository;
    @Autowired private WorkoutSessionExerciseRepository workoutSessionExerciseRepository;
    @Autowired private PersonalRecordRepository personalRecordRepository;
    @Autowired private SharedPersonalRecordRepository sharedPersonalRecordRepository;
    @Autowired private AiConversationRepository userAiConversationRepository;
    @Autowired private AiMessageRepository userAiMessageRepository;
    @Autowired private ChatConversationRepository chatConversationRepository;
    @Autowired private ChatMessageRepository chatMessageRepository;
    @Autowired private UserPresenceRepository userPresenceRepository;
    @Autowired private AdminAiAuditLogRepository adminAiAuditLogRepository;
    @Autowired private AdminAiRecommendationRepository adminAiRecommendationRepository;
    @Autowired private AdminAiMessageRepository adminAiMessageRepository;
    @Autowired private AdminAiConversationRepository adminAiConversationRepository;

    @MockBean
    private GeminiApiClient geminiApiClient;

    private User adminUser;
    private User coachUser;
    private User aliceClient;
    private User bobInactiveClient;
    private User charlieDecliningClient;
    private Exercise plancheEx;
    private Exercise pushUpEx;
    private String adminToken;
    private String coachToken;
    private String aliceToken;

    private void cleanDb() {
        adminAiAuditLogRepository.deleteAll();
        adminAiRecommendationRepository.deleteAll();
        adminAiMessageRepository.deleteAll();
        adminAiConversationRepository.deleteAll();
        userPresenceRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatConversationRepository.deleteAll();
        userAiMessageRepository.deleteAll();
        userAiConversationRepository.deleteAll();
        sharedPersonalRecordRepository.deleteAll();
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

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        when(geminiApiClient.planAdminTools(anyString(), anyList())).thenReturn(Optional.empty());
        when(geminiApiClient.callAdminAgent(anyString(), anyList())).thenReturn(Optional.empty());

        cleanDb();

        adminUser = userRepository.save(User.builder()
                .email("admin.global@calisthenics.test")
                .passwordHash("$2a$10$AdminSecretHash")
                .fullName("Platform Admin")
                .role(Role.ROLE_ADMIN)
                .experienceLevel("ADVANCED")
                .build());

        coachUser = userRepository.save(User.builder()
                .email("coach.marco@private-domain.com")
                .passwordHash("$2a$10$CoachSecretHash")
                .fullName("Coach Marco")
                .role(Role.ROLE_USER)
                .experienceLevel("ELITE")
                .build());
        coachProfileRepository.save(CoachProfile.builder()
                .user(coachUser)
                .displayName("Coach Marco")
                .specialties("Planche, Front Lever")
                .yearsExperience(8)
                .isPublic(true)
                .isAcceptingClients(true)
                .isVerified(true)
                .build());

        aliceClient = userRepository.save(User.builder()
                .email("secret.alice@private-domain.com")
                .passwordHash("$2a$10$SuperSecretHashValue")
                .fullName("Alice Nguyen")
                .role(Role.ROLE_USER)
                .experienceLevel("INTERMEDIATE")
                .build());
        clientProfileRepository.save(ClientProfile.builder()
                .user(aliceClient)
                .displayName("Alice N")
                .experienceLevel("INTERMEDIATE")
                .trainingExperience("2 years street workout")
                .primaryGoals("Planche, Muscle-Up")
                .trainingInterests("Planche, Handstand")
                .preferredFrequency("4 days/week")
                .heightCm(new BigDecimal("164.0"))
                .build());
        clientBodyMeasurementRepository.save(ClientBodyMeasurement.builder()
                .client(aliceClient)
                .weightKg(new BigDecimal("57.5"))
                .bodyFatPercentage(new BigDecimal("17.2"))
                .measuredAt(ZonedDateTime.now().minusDays(2))
                .build());

        bobInactiveClient = userRepository.save(User.builder()
                .email("bob.inactive@private-domain.com")
                .passwordHash("$2a$10$BobSecretHash")
                .fullName("Bob Inactive")
                .role(Role.ROLE_USER)
                .experienceLevel("BEGINNER")
                .build());
        clientProfileRepository.save(ClientProfile.builder()
                .user(bobInactiveClient)
                .displayName("Bob B")
                .experienceLevel("BEGINNER")
                .primaryGoals("Planche")
                .trainingInterests("Planche")
                .preferredFrequency("2 days/week")
                .build());

        charlieDecliningClient = userRepository.save(User.builder()
                .email("charlie.declining@private-domain.com")
                .passwordHash("$2a$10$CharlieSecretHash")
                .fullName("Charlie Declining")
                .role(Role.ROLE_USER)
                .experienceLevel("BEGINNER")
                .build());
        clientProfileRepository.save(ClientProfile.builder()
                .user(charlieDecliningClient)
                .displayName("Charlie C")
                .experienceLevel("BEGINNER")
                .primaryGoals("Strength")
                .build());

        plancheEx = exerciseRepository.save(Exercise.builder()
                .name("Tuck Planche")
                .code("TUCK_PLANCHE")
                .category(ExerciseCategory.SKILL)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .equipment("Parallettes")
                .active(true)
                .build());
        pushUpEx = exerciseRepository.save(Exercise.builder()
                .name("Standard Push-Up")
                .code("STANDARD_PUSH_UP")
                .category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER)
                .equipment("None")
                .active(true)
                .build());

        CoachClientRelationship activeRel = relationshipRepository.save(CoachClientRelationship.builder()
                .coach(coachUser)
                .client(aliceClient)
                .requestedBy(aliceClient)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .build());

        WorkoutPlan alicePlan = workoutPlanRepository.save(WorkoutPlan.builder()
                .user(coachUser)
                .name("Alice Planche Progression Plan")
                .splitType(WorkoutSplitType.CUSTOM)
                .active(true)
                .aiGenerated(false)
                .build());

        WorkoutPlanAssignment assignment = assignmentRepository.save(WorkoutPlanAssignment.builder()
                .relationship(activeRel)
                .workoutPlan(alicePlan)
                .coach(coachUser)
                .client(aliceClient)
                .status(WorkoutPlanAssignmentStatus.ACTIVE)
                .startDate(LocalDate.now().minusDays(20))
                .notes("Keep scapular protraction cue")
                .build());

        // Alice: active recent workouts + high Planche PR improvement
        saveSessionWithExercise(aliceClient, alicePlan, assignment, "Planche Day 1",
                WorkoutSessionStatus.COMPLETED, LocalDate.now().minusDays(1), ZonedDateTime.now().minusDays(1), 2700L, plancheEx, WorkoutSessionExerciseStatus.COMPLETED);
        saveSessionWithExercise(aliceClient, alicePlan, assignment, "Push Foundation",
                WorkoutSessionStatus.COMPLETED, LocalDate.now().minusDays(4), ZonedDateTime.now().minusDays(4), 2400L, pushUpEx, WorkoutSessionExerciseStatus.COMPLETED);
        saveSessionWithExercise(aliceClient, alicePlan, assignment, "Planche Day 2",
                WorkoutSessionStatus.IN_PROGRESS, LocalDate.now().minusDays(8), ZonedDateTime.now().minusDays(8), null, plancheEx, WorkoutSessionExerciseStatus.INCOMPLETE);

        // Bob: inactive (last completed workout 22 days ago) + abandoned recent workout + Planche interest + below 50% completion
        saveSessionWithExercise(bobInactiveClient, null, null, "Old Planche Intro",
                WorkoutSessionStatus.COMPLETED, LocalDate.now().minusDays(22), ZonedDateTime.now().minusDays(22), 1500L, plancheEx, WorkoutSessionExerciseStatus.COMPLETED);
        saveSessionWithExercise(bobInactiveClient, null, null, "Abandoned Attempt 1",
                WorkoutSessionStatus.CANCELLED, LocalDate.now().minusDays(2), ZonedDateTime.now().minusDays(2), null, plancheEx, WorkoutSessionExerciseStatus.SKIPPED);
        saveSessionWithExercise(bobInactiveClient, null, null, "Abandoned Attempt 2",
                WorkoutSessionStatus.IN_PROGRESS, LocalDate.now().minusDays(3), ZonedDateTime.now().minusDays(3), null, pushUpEx, WorkoutSessionExerciseStatus.INCOMPLETE);

        // Charlie: declining consistency (3 workouts 18-24 days ago, 0 completed in last 14 days -> actually inactive & declining)
        for (int d : new int[]{18, 20, 22, 24}) {
            saveSessionWithExercise(charlieDecliningClient, null, null, "Prior Block " + d,
                    WorkoutSessionStatus.COMPLETED, LocalDate.now().minusDays(d), ZonedDateTime.now().minusDays(d), 1800L, pushUpEx, WorkoutSessionExerciseStatus.COMPLETED);
        }

        // Personal Records: Alice has +65% recent Planche PR shared with coach; Bob has stale PR (45 days ago)
        PersonalRecord alicePr = personalRecordRepository.save(PersonalRecord.builder()
                .user(aliceClient)
                .exercise(plancheEx)
                .recordType(PersonalRecordType.MAX_HOLD)
                .recordValue(new BigDecimal("22.00"))
                .previousRecordValue(new BigDecimal("13.33"))
                .improvementPercentage(new BigDecimal("65.04"))
                .unit("seconds")
                .achievedAt(ZonedDateTime.now().minusDays(3))
                .build());
        sharedPersonalRecordRepository.save(SharedPersonalRecord.builder()
                .personalRecord(alicePr)
                .client(aliceClient)
                .coach(coachUser)
                .coachFeedback("Clean hip height!")
                .feedbackAt(ZonedDateTime.now().minusDays(2))
                .rating(5)
                .status("REVIEWED")
                .build());

        personalRecordRepository.save(PersonalRecord.builder()
                .user(bobInactiveClient)
                .exercise(pushUpEx)
                .recordType(PersonalRecordType.MAX_REPS)
                .recordValue(new BigDecimal("15.00"))
                .previousRecordValue(new BigDecimal("10.00"))
                .improvementPercentage(new BigDecimal("50.00"))
                .unit("reps")
                .achievedAt(ZonedDateTime.now().minusDays(45))
                .build());

        // Sensitive private chat & AI messages for Privacy Test
        ChatConversation chatConv = chatConversationRepository.save(ChatConversation.builder()
                .coach(coachUser)
                .client(aliceClient)
                .lastMessageAt(ZonedDateTime.now().minusHours(5))
                .build());
        chatMessageRepository.save(ChatMessage.builder()
                .conversation(chatConv)
                .sender(aliceClient)
                .content("SECRET_PRIVATE_CHAT_MESSAGE_XYZ")
                .messageType(ChatMessageType.TEXT)
                .build());

        AiConversation aiConv = userAiConversationRepository.save(AiConversation.builder()
                .user(aliceClient)
                .title("Alice Private Injury Question")
                .build());
        userAiMessageRepository.save(AiMessage.builder()
                .conversation(aiConv)
                .role("user")
                .intent("INJURY_GUIDANCE")
                .content("SECRET_PRIVATE_AI_PROMPT_999")
                .build());

        userPresenceRepository.save(UserPresenceRecord.builder()
                .userId(aliceClient.getId())
                .status("ONLINE")
                .lastSeen(ZonedDateTime.now().minusMinutes(5))
                .build());

        adminToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(adminUser));
        coachToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(coachUser));
        aliceToken = jwtTokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(aliceClient));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        cleanDb();
    }

    private void saveSessionWithExercise(
            User user,
            WorkoutPlan plan,
            WorkoutPlanAssignment assignment,
            String title,
            WorkoutSessionStatus status,
            LocalDate workoutDate,
            ZonedDateTime timestamp,
            Long durationSeconds,
            Exercise ex,
            WorkoutSessionExerciseStatus exStatus
    ) {
        WorkoutSession ws = WorkoutSession.builder()
                .user(user)
                .workoutPlan(plan)
                .workoutPlanAssignment(assignment)
                .title(title)
                .status(status)
                .workoutDate(workoutDate)
                .startedAt(timestamp)
                .completedAt(status == WorkoutSessionStatus.COMPLETED ? timestamp : null)
                .durationSeconds(durationSeconds)
                .build();
        WorkoutSessionExercise wse = WorkoutSessionExercise.builder()
                .workoutSession(ws)
                .exercise(ex)
                .exerciseNameSnapshot(ex.getName())
                .orderIndex(1)
                .targetSets(3)
                .targetReps(10)
                .restSeconds(90)
                .status(exStatus)
                .build();
        ws.getExercises().add(wse);
        workoutSessionRepository.save(ws);
    }

    private void authenticateAs(User user) {
        UserPrincipal principal = UserPrincipal.create(user);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    // 1. Global user statistics accuracy
    @Test
    void test1_globalUserStatisticsAccuracyAndAuditLog() throws Exception {
        mockMvc.perform(get("/api/v1/admin/ai/analytics/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRegisteredUsers").value(5))
                .andExpect(jsonPath("$.totalAdmins").value(1))
                .andExpect(jsonPath("$.totalCoaches").value(1))
                .andExpect(jsonPath("$.verifiedCoaches").value(1))
                .andExpect(jsonPath("$.totalClients").value(3))
                .andExpect(jsonPath("$.usersWithActiveCoach").value(1))
                .andExpect(jsonPath("$.inactiveUsers14PlusDays").value(4)) // admin, coach, bob, charlie have no completed workouts in 14d
                .andExpect(jsonPath("$.totalPersonalRecordsAllTime").value(2))
                .andExpect(jsonPath("$.timeRange.range").value("LAST_30_DAYS"))
                .andExpect(jsonPath("$.missingDataNotes", hasItem(containsString("available_equipment"))));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("How many users are currently registered?")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("USER_STATISTICS_AND_SEARCH"))
                .andExpect(jsonPath("$.userStatistics.totalRegisteredUsers").value(5))
                .andExpect(jsonPath("$.executedTools[*].toolName", hasItem("get_user_statistics")))
                .andExpect(jsonPath("$.assistantMessage.content", containsString("5")));

        assertTrue(adminAiAuditLogRepository.findTop50ByOrderByCreatedAtDesc().stream()
                .anyMatch(l -> "TOOL_GET_USER_STATISTICS".equals(l.getAction())));
    }

    // 2. Filtering, searching, and segmentation across users
    @Test
    void test2_filteringSearchingAndNaturalLanguageSegmentation() throws Exception {
        // Inactive 14+ days filter via /users
        mockMvc.perform(get("/api/v1/admin/ai/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("activityStatus", "INACTIVE")
                        .param("inactiveDaysThreshold", "14"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].userId", hasItem(bobInactiveClient.getId().intValue())))
                .andExpect(jsonPath("$.items[*].userId", not(hasItem(aliceClient.getId().intValue()))));

        // Combined segment filter: BEGINNER + Planche + NO_COACH + maxCompletionRate 50%
        AdminUserSegmentFilter customSegment = AdminUserSegmentFilter.builder()
                .experienceLevel("BEGINNER")
                .skillKeyword("Planche")
                .coachRelationshipStatus("NO_COACH")
                .maxCompletionRate(50.0)
                .build();

        mockMvc.perform(post("/api/v1/admin/ai/analytics/segments")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(customSegment)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedCount").value(1))
                .andExpect(jsonPath("$.users[0].userId").value(bobInactiveClient.getId()))
                .andExpect(jsonPath("$.users[0].maskedEmail").value("bo***@private-domain.com"))
                .andExpect(jsonPath("$.users[0].email").doesNotExist());

        // Natural-language chat segmentation query
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Show beginner users with Planche goals who have no coach and completion rate below 50%")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.segmentAnalysis.matchedCount").value(1))
                .andExpect(jsonPath("$.matchedUsers[0].fullName").value("Bob Inactive"));
    }

    // 3. Individual user drill-down
    @Test
    void test3_individualUserDrillDownAndSubTools() throws Exception {
        // Masked email by default
        mockMvc.perform(get("/api/v1/admin/ai/users/" + aliceClient.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(aliceClient.getId()))
                .andExpect(jsonPath("$.fullName").value("Alice Nguyen"))
                .andExpect(jsonPath("$.maskedEmail").value("se***@private-domain.com"))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.heightCm").value(164.0))
                .andExpect(jsonPath("$.latestWeightKg").value(57.5))
                .andExpect(jsonPath("$.inferredAvailableEquipment", hasItem("Parallettes")))
                .andExpect(jsonPath("$.workoutSummary.completedSessions").value(2))
                .andExpect(jsonPath("$.personalRecordsSummary.totalRecords").value(1))
                .andExpect(jsonPath("$.personalRecordsSummary.coachFeedbackCount").value(1))
                .andExpect(jsonPath("$.activitySummary.aiMessagesInRange").value(1))
                .andExpect(jsonPath("$.activitySummary.chatMessagesSentInRange").value(1))
                .andExpect(jsonPath("$.missingDataFields", hasItem(containsString("availableEquipment"))));

        // Authorized explicit email retrieval
        mockMvc.perform(get("/api/v1/admin/ai/users/" + aliceClient.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .param("includeEmail", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("secret.alice@private-domain.com"));

        // Sub-endpoints: workouts, personal-records, progress, activity (no private message content)
        mockMvc.perform(get("/api/v1/admin/ai/users/" + bobInactiveClient.getId() + "/workouts")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSessions").value(3))
                .andExpect(jsonPath("$.completedSessions").value(1))
                .andExpect(jsonPath("$.abandonedOrIncompleteWorkouts.length()").value(2));

        mockMvc.perform(get("/api/v1/admin/ai/users/" + aliceClient.getId() + "/personal-records")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.records[0].exerciseName").value("Tuck Planche"))
                .andExpect(jsonPath("$.sharingAndFeedbackHistory[0].coachFeedback").value("Clean hip height!"));

        mockMvc.perform(get("/api/v1/admin/ai/users/" + aliceClient.getId() + "/activity")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topAiIntents[0].label").value("INJURY_GUIDANCE"))
                .andExpect(content().string(not(containsString("SECRET_PRIVATE_CHAT_MESSAGE_XYZ"))))
                .andExpect(content().string(not(containsString("SECRET_PRIVATE_AI_PROMPT_999"))));
    }

    // 4. Aggregated workout & PR trend analysis + coaches + experience comparison
    @Test
    void test4_trendsTopPlancheImproversDecliningConsistencyAndExperienceComparison() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Which users have improved their Planche performance the most?")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformEngagement.topSkillImprovers[0].fullName").value("Alice Nguyen"))
                .andExpect(jsonPath("$.platformEngagement.topSkillImprovers[0].exerciseName").value("Tuck Planche"));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Identify users whose training consistency has declined.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformEngagement.decliningConsistencyUsers[*].userId",
                        hasItem(charlieDecliningClient.getId().intValue())));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("How many users have active coaches?")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intent").value("COACH_CLIENT_ANALYTICS"))
                .andExpect(jsonPath("$.coachClientStatistics.activeRelationships").value(1))
                .andExpect(jsonPath("$.coachClientStatistics.clientsWithActiveCoach").value(1));

        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Compare user engagement across different experience levels.")
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.platformEngagement.experienceComparisons",
                        hasItem(hasEntry("experienceLevel", "INTERMEDIATE"))))
                .andExpect(jsonPath("$.platformEngagement.experienceComparisons",
                        hasItem(hasEntry("experienceLevel", "BEGINNER"))));
    }

    // 5. Administrative recommendation generation based on global data
    @Test
    void test5_proactiveAdminRecommendationsReadOnlyWithoutUserMutation() throws Exception {
        long userCountBefore = userRepository.count();
        long workoutPlanCountBefore = workoutPlanRepository.count();

        mockMvc.perform(post("/api/v1/admin/ai/tools/execute")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminToolExecuteRequest.builder()
                                .toolName("generate_admin_recommendations")
                                .range(AdminAnalyticsRange.LAST_30_DAYS)
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.execution.toolName").value("generate_admin_recommendations"))
                .andExpect(jsonPath("$.execution.status").value("EXECUTED"))
                .andExpect(jsonPath("$.data.length()", greaterThan(0)));

        // Global data access never mutates users, roles, or workout plans
        assertEquals(userCountBefore, userRepository.count());
        assertEquals(workoutPlanCountBefore, workoutPlanRepository.count());
    }

    // 6. Unauthorized access rejection at HTTP endpoints AND independent tool/service boundary
    @Test
    void test6_unauthorizedAccessRejectedEverywhere() throws Exception {
        // HTTP RBAC for regular user & coach user & anonymous
        for (String endpoint : List.of(
                "/api/v1/admin/ai/analytics/users",
                "/api/v1/admin/ai/users",
                "/api/v1/admin/ai/users/" + aliceClient.getId(),
                "/api/v1/admin/ai/analytics/coaching",
                "/api/v1/admin/ai/analytics/engagement",
                "/api/v1/admin/ai/audit-logs"
        )) {
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + aliceToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(endpoint).header("Authorization", "Bearer " + coachToken))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized());
        }

        // Direct tool/service invocation WITHOUT admin authority must throw AccessDeniedException
        authenticateAs(aliceClient);
        assertThrows(AccessDeniedException.class, () ->
                adminDataAccessService.getUserStatistics(AdminAnalyticsRange.LAST_30_DAYS, null, null));
        assertThrows(AccessDeniedException.class, () ->
                adminDataAccessService.getUserDetails(aliceClient.getId(), true, AdminAnalyticsRange.LAST_30_DAYS, null, null));
        assertThrows(AccessDeniedException.class, () ->
                adminToolRouter.executeSingleTool(AdminToolExecuteRequest.builder()
                        .toolName(AdminToolName.ANALYZE_USER_SEGMENTS.getToolName())
                        .build()));

        // Coaches also have ROLE_USER and must be rejected independently
        authenticateAs(coachUser);
        assertThrows(AccessDeniedException.class, () ->
                adminDataAccessService.getCoachClientStatistics(AdminAnalyticsRange.LAST_30_DAYS, null, null));
    }

    // 7. Large dataset handling with pagination and bounded O(1) SQL queries (zero N+1)
    @Test
    void test7_largeDatasetPaginationAndNoNPlusOneQueries() throws Exception {
        for (int i = 1; i <= 62; i++) {
            User bulkUser = userRepository.save(User.builder()
                    .email("bulk.athlete." + i + "@calisthenics.test")
                    .passwordHash("$2a$10$BulkHash")
                    .fullName("Bulk Athlete " + i)
                    .role(Role.ROLE_USER)
                    .experienceLevel(i % 2 == 0 ? "INTERMEDIATE" : "BEGINNER")
                    .build());
            clientProfileRepository.save(ClientProfile.builder()
                    .user(bulkUser)
                    .experienceLevel(bulkUser.getExperienceLevel())
                    .primaryGoals("Handstand")
                    .build());
            saveSessionWithExercise(bulkUser, null, null, "Bulk Session " + i,
                    WorkoutSessionStatus.COMPLETED, LocalDate.now().minusDays(i % 10),
                    ZonedDateTime.now().minusDays(i % 10), 1800L, pushUpEx, WorkoutSessionExerciseStatus.COMPLETED);
        }

        // Page 0 of size 20 out of 67 total users
        mockMvc.perform(get("/api/v1/admin/ai/users")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.totalElements").value(67))
                .andExpect(jsonPath("$.totalPages").value(4))
                .andExpect(jsonPath("$.hasNext").value(true));

        // Verify Hibernate SQL prepared statement count is strictly O(1) (<< 67)
        SessionFactory sf = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics stats = sf.getStatistics();
        stats.setStatisticsEnabled(true);
        stats.clear();

        authenticateAs(adminUser);
        AdminPagedResult<AdminGlobalUserSummaryDto> pageResult = adminDataAccessService.getAllUsers(
                AdminUserSegmentFilter.builder().page(0).size(20).build());
        long statementsForPagedUsers = stats.getPrepareStatementCount();

        assertEquals(20, pageResult.getItems().size());
        assertTrue(statementsForPagedUsers <= 18,
                "Expected O(1) batch SQL queries (<=18) for paginated users, got " + statementsForPagedUsers);

        stats.clear();
        AdminGlobalUserStatisticsResponse globalStats = adminDataAccessService.getUserStatistics(
                AdminAnalyticsRange.LAST_30_DAYS, null, null);
        long statementsForStats = stats.getPrepareStatementCount();
        assertEquals(67, globalStats.getTotalRegisteredUsers());
        assertTrue(statementsForStats <= 25,
                "Expected constant aggregate SQL queries (<=25) for user statistics, got " + statementsForStats);
    }

    // 8. Privacy protection: ensure emails, passwords, and private chat/AI text are excluded from AI prompts
    @Test
    @SuppressWarnings("unchecked")
    void test8_privacyProtectionNoSensitivePiiOrPrivateMessagesInLlmPrompts() throws Exception {
        mockMvc.perform(post("/api/v1/admin/ai/chat")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(AdminChatRequest.builder()
                                .message("Inspect user #" + aliceClient.getId() + " and summarize engagement")
                                .targetUserId(aliceClient.getId())
                                .includeEmail(true) // even if response includes email for authorized admin, LLM prompt must NOT!
                                .build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inspectedUserDetail.userId").value(aliceClient.getId()));

        ArgumentCaptor<String> systemPromptCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<GeminiRequestDto.Content>> contentsCaptor = ArgumentCaptor.forClass(List.class);
        verify(geminiApiClient, atLeastOnce()).callAdminAgent(systemPromptCaptor.capture(), contentsCaptor.capture());

        String capturedSystemPrompt = systemPromptCaptor.getValue();
        String serializedContents = objectMapper.writeValueAsString(contentsCaptor.getValue());
        String combinedPromptPayload = capturedSystemPrompt + "\n" + serializedContents;

        assertFalse(combinedPromptPayload.contains("secret.alice@private-domain.com"), "Raw user email leaked to LLM prompt");
        assertFalse(combinedPromptPayload.contains("$2a$10$SuperSecretHashValue"), "Password hash leaked to LLM prompt");
        assertFalse(combinedPromptPayload.contains("SECRET_PRIVATE_CHAT_MESSAGE_XYZ"), "Private user chat content leaked to LLM prompt");
        assertFalse(combinedPromptPayload.contains("SECRET_PRIVATE_AI_PROMPT_999"), "Private user AI chat content leaked to LLM prompt");
    }
}
