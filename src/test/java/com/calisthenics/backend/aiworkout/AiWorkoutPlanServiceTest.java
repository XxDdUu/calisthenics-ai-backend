package com.calisthenics.backend.aiworkout;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.entity.AiWorkoutPlanLog;
import com.calisthenics.backend.aiworkout.exception.AiWorkoutPlanValidationException;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.aiworkout.repository.AiWorkoutPlanLogRepository;
import com.calisthenics.backend.aiworkout.service.impl.AiWorkoutPlanServiceImpl;
import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanPromptAnalyzer;
import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanValidator;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workoutplan.dto.request.CreateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanService;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiWorkoutPlanServiceTest {

    @Mock
    private GeminiApiClient geminiApiClient;

    @Mock
    private ExerciseRepository exerciseRepository;

    @Mock
    private WorkoutPlanRepository workoutPlanRepository;

    @Mock
    private WorkoutPlanDayRepository workoutPlanDayRepository;

    @Mock
    private WorkoutPlanService workoutPlanService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CoachClientRelationshipRepository relationshipRepository;

    @Mock
    private AiWorkoutPlanLogRepository aiWorkoutPlanLogRepository;

    @Mock
    private WorkoutSessionRepository sessionRepository;

    private ObjectMapper objectMapper;
    private AppProperties appProperties;
    private AiWorkoutPlanPromptAnalyzer promptAnalyzer;
    private AiWorkoutPlanValidator validator;
    private UserTimeResolver userTimeResolver;
    private AiWorkoutPlanServiceImpl aiWorkoutPlanService;

    private User testAthlete;
    private User testCoach;
    private List<Exercise> exerciseCatalog;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        appProperties = new AppProperties();
        promptAnalyzer = new AiWorkoutPlanPromptAnalyzer(appProperties);
        validator = new AiWorkoutPlanValidator(appProperties, promptAnalyzer);
        userTimeResolver = new UserTimeResolver(
                appProperties,
                Clock.fixed(Instant.parse("2026-11-10T05:00:00Z"), ZoneOffset.UTC)
        );
        aiWorkoutPlanService = new AiWorkoutPlanServiceImpl(
                geminiApiClient,
                exerciseRepository,
                workoutPlanRepository,
                workoutPlanDayRepository,
                workoutPlanService,
                userRepository,
                relationshipRepository,
                aiWorkoutPlanLogRepository,
                sessionRepository,
                objectMapper,
                promptAnalyzer,
                validator,
                userTimeResolver,
                appProperties
        );

        testAthlete = User.builder()
                .id(10L)
                .email("athlete@test.com")
                .fullName("Test Athlete")
                .role(Role.ROLE_USER)
                .build();

        testCoach = User.builder()
                .id(20L)
                .email("coach@test.com")
                .fullName("Test Coach")
                .role(Role.ROLE_USER)
                .build();

        exerciseCatalog = List.of(
                Exercise.builder().id(1L).name("Push-Up").code("PUSH_UP").category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.BEGINNER).active(true).build(),
                Exercise.builder().id(2L).name("Bodyweight Squat").code("SQUAT").category(ExerciseCategory.LEGS).difficulty(DifficultyLevel.BEGINNER).active(true).build(),
                Exercise.builder().id(3L).name("Pull-Up").code("PULL_UP").category(ExerciseCategory.PULL).difficulty(DifficultyLevel.INTERMEDIATE).active(true).build(),
                Exercise.builder().id(4L).name("Parallel Bar Dips").code("DIPS").category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.BEGINNER).active(true).build(),
                Exercise.builder().id(5L).name("Handstand").code("HANDSTAND").category(ExerciseCategory.SKILL).difficulty(DifficultyLevel.INTERMEDIATE).active(true).build(),
                Exercise.builder().id(6L).name("Planche").code("PLANCHE").category(ExerciseCategory.SKILL).difficulty(DifficultyLevel.ADVANCED).active(true).build(),
                Exercise.builder().id(7L).name("Front Lever").code("FRONT_LEVER").category(ExerciseCategory.SKILL).difficulty(DifficultyLevel.ADVANCED).active(true).build(),
                Exercise.builder().id(8L).name("Pike Push-Up").code("PIKE_PUSH_UP").category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.INTERMEDIATE).active(true).build(),
                Exercise.builder().id(9L).name("L-Sit").code("L_SIT").category(ExerciseCategory.CORE).difficulty(DifficultyLevel.INTERMEDIATE).active(true).build()
        );
    }

    @Test
    @DisplayName("Generate 6-day plan with planche static holds and rep exercises")
    void testGenerate6DayPlanchePlan() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testAthlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(exerciseCatalog);
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(10L)).thenReturn(Optional.empty());

        GeminiStructuredWorkoutPlan geminiPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("6-Day Planche & Front Lever Split")
                .description("Specialized calisthenics skill program")
                .durationWeeks(4)
                .splitType("CUSTOM")
                .sessions(List.of(
                        session("MONDAY", "Planche + Push", false, List.of(
                                holdEx(6L, "Planche", 4, 15, 180),
                                repEx(1L, "Push-Up", 4, 12, 90)
                        )),
                        session("TUESDAY", "Front Lever + Pull", false, List.of(
                                holdEx(7L, "Front Lever", 4, 12, 180),
                                repEx(3L, "Pull-Up", 4, 8, 120)
                        )),
                        session("WEDNESDAY", "Legs & Core", false, List.of(
                                repEx(2L, "Bodyweight Squat", 4, 15, 90),
                                holdEx(9L, "L-Sit", 3, 20, 90)
                        )),
                        session("THURSDAY", "Handstand & Push", false, List.of(
                                holdEx(5L, "Handstand", 5, 20, 90),
                                repEx(8L, "Pike Push-Up", 4, 8, 120)
                        )),
                        session("FRIDAY", "Planche Strength", false, List.of(
                                holdEx(6L, "Planche", 4, 15, 180),
                                repEx(4L, "Parallel Bar Dips", 4, 10, 120)
                        )),
                        session("SATURDAY", "Front Lever Strength", false, List.of(
                                holdEx(7L, "Front Lever", 4, 12, 180),
                                repEx(3L, "Pull-Up", 4, 8, 120)
                        )),
                        session("SUNDAY", "Rest Day", true, Collections.emptyList())
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString())).thenReturn(
                new GeminiApiClient.GeminiCallResult(geminiPlan, "{}", 200, 500, 350L, "gemini-2.5-flash-lite")
        );

        GenerateAiWorkoutPlanRequest request = GenerateAiWorkoutPlanRequest.builder()
                .prompt("Create a 6-day calisthenics workout plan focusing on planche and front lever")
                .durationWeeks(4)
                .build();

        AiWorkoutPlanPreviewResponse preview = aiWorkoutPlanService.generatePlan(10L, request);

        assertNotNull(preview);
        assertEquals("6-Day Planche & Front Lever Split", preview.getPlanName());
        assertEquals(4, preview.getDurationWeeks());
        assertEquals(WorkoutSplitType.CUSTOM, preview.getSplitType());
        assertFalse(preview.getMetadata().isHasConflicts());
        assertEquals(6, preview.getSessions().stream().filter(s -> !s.isRestDay()).count());

        AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview monday = preview.getSessions().stream()
                .filter(s -> s.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst().orElseThrow();
        assertEquals(2, monday.getExercises().size());

        AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview planche = monday.getExercises().get(0);
        assertEquals(6L, planche.getExerciseId());
        assertEquals(15, planche.getHoldSeconds());
        assertNull(planche.getReps());

        AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview pushUp = monday.getExercises().get(1);
        assertEquals(1L, pushUp.getExerciseId());
        assertEquals(12, pushUp.getReps());
        assertNull(pushUp.getHoldSeconds());

        AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview sunday = preview.getSessions().stream()
                .filter(s -> s.getDayOfWeek() == WorkoutDayOfWeek.SUNDAY)
                .findFirst().orElseThrow();
        assertTrue(sunday.isRestDay());
        assertTrue(sunday.getExercises().isEmpty());
    }

    @Test
    @DisplayName("Detect existing active plan conflict during generation")
    void testDetectScheduleConflict() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testAthlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(exerciseCatalog);

        WorkoutPlan existingActivePlan = WorkoutPlan.builder()
                .id(99L)
                .name("Old PPL Program")
                .active(true)
                .build();
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(10L))
                .thenReturn(Optional.of(existingActivePlan));

        GeminiStructuredWorkoutPlan geminiPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("New 3-Day Plan")
                .durationWeeks(4)
                .sessions(List.of(
                        session("MONDAY", "Full Body A", false, List.of(repEx(1L, "Push-Up", 3, 12, 90), repEx(2L, "Bodyweight Squat", 3, 15, 90))),
                        session("WEDNESDAY", "Full Body B", false, List.of(repEx(3L, "Pull-Up", 3, 8, 120), holdEx(9L, "L-Sit", 3, 15, 90))),
                        session("FRIDAY", "Full Body C", false, List.of(repEx(4L, "Parallel Bar Dips", 3, 10, 120), holdEx(5L, "Handstand", 3, 20, 90)))
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString())).thenReturn(
                new GeminiApiClient.GeminiCallResult(geminiPlan, "{}", 100, 200, 200L, "gemini-2.5-flash-lite")
        );

        GenerateAiWorkoutPlanRequest request = GenerateAiWorkoutPlanRequest.builder()
                .prompt("3-day calisthenics routine")
                .build();

        AiWorkoutPlanPreviewResponse preview = aiWorkoutPlanService.generatePlan(10L, request);

        assertTrue(preview.getMetadata().isHasConflicts());
        assertEquals("Old PPL Program", preview.getMetadata().getExistingActivePlanName());
        assertEquals(3, preview.getSessions().stream().filter(s -> !s.isRestDay()).count());
    }

    @Test
    @DisplayName("Exact catalog matching resolves missing ID by exact name and rejects unknown/fallback exercises")
    void testExerciseMatchingRejectsUnknownAndDoesNotFallbackToFirstCatalog() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testAthlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(exerciseCatalog);

        GeminiStructuredWorkoutPlan invalidPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("Invalid Exercise Plan")
                .durationWeeks(4)
                .sessions(List.of(
                        session("MONDAY", "Push Day", false, List.of(
                                repEx(null, "Push-Up", 3, 10, 90),
                                repEx(9999L, "Unknown Made Up Movement", 3, 10, 90)
                        ))
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString())).thenReturn(
                new GeminiApiClient.GeminiCallResult(invalidPlan, "{}", 100, 200, 200L, "gemini-flash-lite-latest")
        );

        GenerateAiWorkoutPlanRequest request = GenerateAiWorkoutPlanRequest.builder()
                .prompt("simple push workout")
                .build();

        AiWorkoutPlanValidationException ex = assertThrows(
                AiWorkoutPlanValidationException.class,
                () -> aiWorkoutPlanService.generatePlan(10L, request)
        );
        assertFalse(ex.getViolations().isEmpty());
        // Verified 1 initial attempt + 1 controlled correction retry
        verify(geminiApiClient, times(2)).generateWorkoutPlan(anyString(), anyString());
    }

    @Test
    @DisplayName("Coach generates plan for assigned client")
    void testCoachGeneratesPlanForClient() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testAthlete));
        when(relationshipRepository.findActiveRelationshipForUsers(20L, 10L)).thenReturn(
                Optional.of(CoachClientRelationship.builder().status(CoachClientRelationshipStatus.ACTIVE).build())
        );
        when(exerciseRepository.findByActiveTrue()).thenReturn(exerciseCatalog);
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(10L)).thenReturn(Optional.empty());

        GeminiStructuredWorkoutPlan geminiPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("Coach Designed Plan")
                .durationWeeks(6)
                .sessions(List.of(
                        session("MONDAY", "Skill Day", false, List.of(holdEx(5L, "Handstand", 4, 20, 90))),
                        session("THURSDAY", "Push Strength", false, List.of(repEx(4L, "Parallel Bar Dips", 4, 8, 120)))
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString())).thenReturn(
                new GeminiApiClient.GeminiCallResult(geminiPlan, "{}", 150, 300, 250L, "gemini-2.5-flash-lite")
        );

        GenerateAiWorkoutPlanRequest request = GenerateAiWorkoutPlanRequest.builder()
                .prompt("6-week progression for client")
                .clientId(10L)
                .build();

        AiWorkoutPlanPreviewResponse preview = aiWorkoutPlanService.generatePlan(20L, request);
        assertNotNull(preview);
        assertEquals("Coach Designed Plan", preview.getPlanName());
        assertEquals(6, preview.getDurationWeeks());
    }

    @Test
    @DisplayName("Unauthorized coach cannot generate plan for unassigned client")
    void testUnauthorizedCoachForbidden() {
        when(relationshipRepository.findActiveRelationshipForUsers(20L, 10L)).thenReturn(Optional.empty());

        GenerateAiWorkoutPlanRequest request = GenerateAiWorkoutPlanRequest.builder()
                .prompt("Plan for client")
                .clientId(10L)
                .build();

        assertThrows(ResponseStatusException.class, () -> aiWorkoutPlanService.generatePlan(20L, request));
    }

    @Test
    @DisplayName("Commit plan creates active workout plan and schedules future calendar sessions as SCHEDULED")
    void testCommitPlanSuccessfully() {
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testAthlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(exerciseCatalog);
        when(workoutPlanRepository.findByUserIdForUpdate(10L)).thenReturn(Collections.emptyList());

        AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview mondaySession = AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                .title("Push Workout")
                .restDay(false)
                .exercises(List.of(
                        AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview.builder()
                                .exerciseId(1L)
                                .exerciseName("Push-Up")
                                .sets(4)
                                .reps(12)
                                .restSeconds(90)
                                .build()
                ))
                .build();

        AiWorkoutPlanPreviewResponse planPreview = AiWorkoutPlanPreviewResponse.builder()
                .planName("New Strength Split")
                .description("AI Generated plan")
                .durationWeeks(4)
                .splitType(WorkoutSplitType.CUSTOM)
                .startDate(LocalDate.of(2026, 11, 16))
                .sessions(List.of(mondaySession))
                .build();

        CommitAiWorkoutPlanRequest commitRequest = CommitAiWorkoutPlanRequest.builder()
                .plan(planPreview)
                .startDate(LocalDate.of(2026, 11, 16))
                .idempotencyKey("commit-key-12345")
                .replaceExisting(true)
                .build();

        WorkoutPlan createdEntity = WorkoutPlan.builder()
                .id(101L)
                .name("New Strength Split")
                .user(testAthlete)
                .active(true)
                .build();

        WorkoutPlanResponse planResponse = WorkoutPlanResponse.builder()
                .id(101L)
                .name("New Strength Split")
                .active(true)
                .build();

        when(workoutPlanService.create(eq(10L), any(CreateWorkoutPlanRequest.class))).thenReturn(planResponse);
        when(workoutPlanRepository.findById(101L)).thenReturn(Optional.of(createdEntity));
        when(workoutPlanRepository.getReferenceById(101L)).thenReturn(createdEntity);

        WorkoutPlanDay mondayDay = WorkoutPlanDay.builder()
                .id(501L)
                .workoutPlan(createdEntity)
                .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                .name("Push Workout")
                .restDay(false)
                .exercises(Set.of(WorkoutPlanExercise.builder().build()))
                .build();

        when(workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(101L)).thenReturn(List.of(mondayDay));
        when(sessionRepository.existsByUserIdAndWorkoutDateAndStatusIn(eq(10L), any(LocalDate.class), anyList()))
                .thenReturn(false);

        WorkoutPlanResponse response = aiWorkoutPlanService.commitPlan(10L, commitRequest);

        assertNotNull(response);
        assertEquals(101L, response.getId());
        assertTrue(response.isActive());

        ArgumentCaptor<WorkoutSession> sessionCaptor = ArgumentCaptor.forClass(WorkoutSession.class);
        verify(sessionRepository, times(4)).save(sessionCaptor.capture());
        assertTrue(sessionCaptor.getAllValues().stream().allMatch(s -> s.getStatus() == WorkoutSessionStatus.SCHEDULED));

        ArgumentCaptor<AiWorkoutPlanLog> logCaptor = ArgumentCaptor.forClass(AiWorkoutPlanLog.class);
        verify(aiWorkoutPlanLogRepository).save(logCaptor.capture());
        assertEquals("COMMITTED", logCaptor.getValue().getStatus());
        assertEquals("commit-key-12345", logCaptor.getValue().getIdempotencyKey());
    }

    @Test
    @DisplayName("Duplicate commit with same idempotency key returns existing plan without re-inserting")
    void testIdempotentCommit() {
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testAthlete));

        AiWorkoutPlanLog previousLog = AiWorkoutPlanLog.builder()
                .id(55L)
                .idempotencyKey("idemp-unique-999")
                .status("COMMITTED")
                .build();

        when(aiWorkoutPlanLogRepository.findFirstByUserIdAndIdempotencyKeyOrderByCreatedAtDesc(10L, "idemp-unique-999"))
                .thenReturn(Optional.of(previousLog));

        WorkoutPlan activePlan = WorkoutPlan.builder().id(202L).name("Active Plan").build();
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(10L)).thenReturn(Optional.of(activePlan));

        WorkoutPlanResponse expectedResponse = WorkoutPlanResponse.builder().id(202L).name("Active Plan").build();
        when(workoutPlanService.get(10L, 202L)).thenReturn(expectedResponse);

        CommitAiWorkoutPlanRequest request = CommitAiWorkoutPlanRequest.builder()
                .idempotencyKey("idemp-unique-999")
                .plan(AiWorkoutPlanPreviewResponse.builder().planName("Active Plan").sessions(List.of(
                        AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder().dayOfWeek(WorkoutDayOfWeek.MONDAY).build()
                )).build())
                .build();

        WorkoutPlanResponse result = aiWorkoutPlanService.commitPlan(10L, request);

        assertEquals(202L, result.getId());
        verify(workoutPlanService, never()).create(anyLong(), any());
    }

    @Test
    @DisplayName("Generate and commit normalize reps=0 on static hold exercises to null")
    void testNormalizeZeroRepsOnStaticHoldExercise() {
        when(userRepository.findById(10L)).thenReturn(Optional.of(testAthlete));
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testAthlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(exerciseCatalog);
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(10L)).thenReturn(Optional.empty());
        when(workoutPlanRepository.findByUserIdForUpdate(10L)).thenReturn(Collections.emptyList());

        GeminiStructuredWorkoutPlan geminiPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("Planche Specialization")
                .durationWeeks(4)
                .sessions(List.of(
                        session("MONDAY", "Planche Focus", false, List.of(
                                GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                                        .exerciseId(6L)
                                        .exerciseName("Planche")
                                        .sets(4)
                                        .reps(0)
                                        .holdSeconds(12)
                                        .restSeconds(180)
                                        .build()
                        ))
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString())).thenReturn(
                new GeminiApiClient.GeminiCallResult(geminiPlan, "{}", 100, 200, 180L, "gemini-flash-lite-latest")
        );

        AiWorkoutPlanPreviewResponse preview = aiWorkoutPlanService.generatePlan(
                10L, GenerateAiWorkoutPlanRequest.builder().prompt("Planche 4 weeks").build()
        );
        AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview mondayPlanche = preview.getSessions().stream()
                .filter(s -> s.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst().orElseThrow()
                .getExercises().get(0);
        assertNull(mondayPlanche.getReps());
        assertEquals(12, mondayPlanche.getHoldSeconds());

        mondayPlanche.setReps(0);
        WorkoutPlan created = WorkoutPlan.builder().id(301L).name("Planche Specialization").user(testAthlete).active(true).build();
        when(workoutPlanService.create(eq(10L), any(CreateWorkoutPlanRequest.class))).thenReturn(
                WorkoutPlanResponse.builder().id(301L).name("Planche Specialization").active(true).build()
        );
        when(workoutPlanRepository.findById(301L)).thenReturn(Optional.of(created));
        when(workoutPlanRepository.getReferenceById(301L)).thenReturn(created);
        when(workoutPlanDayRepository.findByWorkoutPlanIdOrderByDisplayOrderAsc(301L)).thenReturn(Collections.emptyList());

        aiWorkoutPlanService.commitPlan(10L, CommitAiWorkoutPlanRequest.builder()
                .plan(preview)
                .replaceExisting(true)
                .build());

        ArgumentCaptor<CreateWorkoutPlanRequest> captor = ArgumentCaptor.forClass(CreateWorkoutPlanRequest.class);
        verify(workoutPlanService).create(eq(10L), captor.capture());
        var persistedEx = captor.getValue().getDays().stream()
                .filter(d -> d.getDayOfWeek() == WorkoutDayOfWeek.MONDAY)
                .findFirst().orElseThrow()
                .getExercises().get(0);
        assertNull(persistedEx.getReps());
        assertEquals(12, persistedEx.getDurationSeconds());
    }

    @Test
    @DisplayName("Commit rejects stale expectedActivePlanId with 409 CONFLICT without overwriting active plan")
    void testCommitRejectsStaleActivePlanConflict() {
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testAthlete));
        WorkoutPlan existingActive = WorkoutPlan.builder().id(88L).name("Coach Strength Plan").active(true).build();
        when(workoutPlanRepository.findByUserIdForUpdate(10L)).thenReturn(List.of(existingActive));

        AiWorkoutPlanPreviewResponse preview = AiWorkoutPlanPreviewResponse.builder()
                .planName("New AI Plan")
                .sessions(List.of(
                        AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                                .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                                .restDay(false)
                                .exercises(Collections.emptyList())
                                .build()
                ))
                .build();

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () ->
                aiWorkoutPlanService.commitPlan(10L, CommitAiWorkoutPlanRequest.builder()
                        .plan(preview)
                        .replaceExisting(true)
                        .expectedActivePlanId(0L)
                        .build()));

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        assertTrue(ex.getReason().contains("Coach Strength Plan"));
        assertTrue(existingActive.isActive());
        verify(workoutPlanService, never()).create(anyLong(), any());
    }

    @Test
    @DisplayName("Commit with replaceExisting=false creates inactive plan and keeps existing active plan intact")
    void testCommitReplaceExistingFalseKeepsActivePlan() {
        when(userRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(testAthlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(exerciseCatalog);
        WorkoutPlan existingActive = WorkoutPlan.builder().id(88L).name("Existing Active Plan").active(true).build();
        when(workoutPlanRepository.findByUserIdForUpdate(10L)).thenReturn(List.of(existingActive));

        WorkoutPlan createdEntity = WorkoutPlan.builder().id(305L).name("Saved Draft AI Plan").user(testAthlete).active(false).build();
        when(workoutPlanService.create(eq(10L), any(CreateWorkoutPlanRequest.class))).thenReturn(
                WorkoutPlanResponse.builder().id(305L).name("Saved Draft AI Plan").active(false).build()
        );
        when(workoutPlanRepository.findById(305L)).thenReturn(Optional.of(createdEntity));

        AiWorkoutPlanPreviewResponse preview = AiWorkoutPlanPreviewResponse.builder()
                .planName("Saved Draft AI Plan")
                .durationWeeks(4)
                .startDate(LocalDate.of(2026, 11, 16))
                .sessions(List.of(
                        AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                                .dayOfWeek(WorkoutDayOfWeek.MONDAY)
                                .title("Push Day")
                                .restDay(false)
                                .exercises(List.of(
                                        AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview.builder()
                                                .exerciseId(1L)
                                                .exerciseName("Push-Up")
                                                .sets(3)
                                                .reps(12)
                                                .restSeconds(90)
                                                .build()
                                ))
                                .build()
                ))
                .build();

        WorkoutPlanResponse response = aiWorkoutPlanService.commitPlan(10L, CommitAiWorkoutPlanRequest.builder()
                .plan(preview)
                .replaceExisting(false)
                .build());

        assertFalse(response.isActive());
        assertTrue(existingActive.isActive());
        ArgumentCaptor<CreateWorkoutPlanRequest> captor = ArgumentCaptor.forClass(CreateWorkoutPlanRequest.class);
        verify(workoutPlanService).create(eq(10L), captor.capture());
        assertEquals(Boolean.FALSE, captor.getValue().getActive());
    }

    private GeminiStructuredWorkoutPlan.GeminiSession session(
            String dow,
            String title,
            boolean rest,
            List<GeminiStructuredWorkoutPlan.GeminiExercise> exercises
    ) {
        return GeminiStructuredWorkoutPlan.GeminiSession.builder()
                .dayOfWeek(dow)
                .title(title)
                .restDay(rest)
                .exercises(exercises)
                .build();
    }

    private GeminiStructuredWorkoutPlan.GeminiExercise repEx(Long id, String name, int sets, int reps, int rest) {
        return GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                .exerciseId(id)
                .exerciseName(name)
                .sets(sets)
                .reps(reps)
                .restSeconds(rest)
                .build();
    }

    private GeminiStructuredWorkoutPlan.GeminiExercise holdEx(Long id, String name, int sets, int holdSeconds, int rest) {
        return GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                .exerciseId(id)
                .exerciseName(name)
                .sets(sets)
                .holdSeconds(holdSeconds)
                .restSeconds(rest)
                .build();
    }
}
