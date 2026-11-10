package com.calisthenics.backend.aiworkout;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.exception.AiWorkoutPlanValidationException;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.aiworkout.gemini.dto.GeminiStructuredWorkoutPlan;
import com.calisthenics.backend.aiworkout.repository.AiWorkoutPlanLogRepository;
import com.calisthenics.backend.aiworkout.service.impl.AiWorkoutPlanServiceImpl;
import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanPromptAnalyzer;
import com.calisthenics.backend.aiworkout.validation.AiWorkoutPlanValidator;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.workout.*;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.mapper.WorkoutPlanMapper;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanService;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiWorkoutPlanValidationAndRegressionTest {

    @Mock private GeminiApiClient geminiApiClient;
    @Mock private ExerciseRepository exerciseRepository;
    @Mock private WorkoutPlanRepository workoutPlanRepository;
    @Mock private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Mock private WorkoutPlanService workoutPlanService;
    @Mock private UserRepository userRepository;
    @Mock private CoachClientRelationshipRepository relationshipRepository;
    @Mock private AiWorkoutPlanLogRepository aiWorkoutPlanLogRepository;
    @Mock private WorkoutSessionRepository sessionRepository;

    private AppProperties appProperties;
    private AiWorkoutPlanPromptAnalyzer promptAnalyzer;
    private AiWorkoutPlanValidator validator;
    private UserTimeResolver timeResolver;
    private AiWorkoutPlanServiceImpl service;
    private List<Exercise> catalog;
    private User athlete;

    @BeforeEach
    void setUp() {
        appProperties = new AppProperties();
        promptAnalyzer = new AiWorkoutPlanPromptAnalyzer(appProperties);
        validator = new AiWorkoutPlanValidator(appProperties, promptAnalyzer);
        timeResolver = new UserTimeResolver(
                appProperties,
                Clock.fixed(Instant.parse("2026-11-17T06:00:00Z"), ZoneOffset.UTC)
        );
        service = new AiWorkoutPlanServiceImpl(
                geminiApiClient, exerciseRepository, workoutPlanRepository,
                workoutPlanDayRepository, workoutPlanService, userRepository,
                relationshipRepository, aiWorkoutPlanLogRepository, sessionRepository,
                new ObjectMapper(), promptAnalyzer, validator, timeResolver, appProperties
        );

        athlete = User.builder().id(1L).email("a@test.com").fullName("Athlete").role(Role.ROLE_USER).build();

        // Mirrors real catalog including similar-named distinct exercises (Tuck/Straddle/Full Planche, Planche Push-Up)
        catalog = List.of(
                ex(1L, "Tuck Planche", "TUCK_PLANCHE", ExerciseCategory.SKILL),
                ex(2L, "Advanced Tuck Planche", "ADV_TUCK_PLANCHE", ExerciseCategory.SKILL),
                ex(3L, "Straddle Planche", "STRADDLE_PLANCHE", ExerciseCategory.SKILL),
                ex(4L, "Full Planche", "FULL_PLANCHE", ExerciseCategory.SKILL),
                ex(5L, "Handstand", "HANDSTAND", ExerciseCategory.SKILL),
                ex(6L, "Muscle-Up", "MUSCLE_UP", ExerciseCategory.PULL),
                ex(7L, "Front Lever", "FRONT_LEVER", ExerciseCategory.SKILL),
                ex(8L, "Parallel Bar Dips", "DIPS", ExerciseCategory.PUSH),
                ex(9L, "Planche push up", "PLANCHE_PUSH_UP", ExerciseCategory.PUSH),
                ex(10L, "Front lever pull up", "FRONT_LEVER_PULL_UP", ExerciseCategory.PULL),
                ex(11L, "Push-Up", "PUSH_UP", ExerciseCategory.PUSH),
                ex(12L, "Bodyweight Squat", "SQUAT", ExerciseCategory.LEGS),
                ex(13L, "Pull-Up", "PULL_UP", ExerciseCategory.PULL),
                ex(16L, "Planche", "PLANCHE", ExerciseCategory.SKILL),
                ex(18L, "Pike Push-Up", "PIKE_PUSH_UP", ExerciseCategory.PUSH),
                ex(19L, "L-Sit", "L_SIT", ExerciseCategory.CORE)
        );
    }

    @Test
    @DisplayName("Case A: Generate 3-day/week plan -> exactly 3 training days, <=10 exercises/session, no duplicates")
    void caseA_3DayPerWeekPlanValidatedStrictly() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(athlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(catalog);
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        GeminiStructuredWorkoutPlan valid3Day = GeminiStructuredWorkoutPlan.builder()
                .planName("3-Day Full Body Calisthenics")
                .description("Custom plan powered by Gemini AI") // ensure branding stripped
                .durationWeeks(4)
                .splitType("FULL_BODY")
                .sessions(List.of(
                        training("MONDAY", "Full Body A", List.of(
                                rep(11L, "Push-Up"), rep(13L, "Pull-Up"), rep(12L, "Bodyweight Squat"), hold(19L, "L-Sit")
                        )),
                        rest("TUESDAY"),
                        training("WEDNESDAY", "Full Body B", List.of(
                                rep(8L, "Parallel Bar Dips"), rep(10L, "Front lever pull up"), hold(5L, "Handstand"), rep(18L, "Pike Push-Up")
                        )),
                        rest("THURSDAY"),
                        training("FRIDAY", "Full Body C", List.of(
                                rep(6L, "Muscle-Up"), rep(9L, "Planche push up"), rep(12L, "Bodyweight Squat"), hold(7L, "Front Lever")
                        )),
                        rest("SATURDAY"),
                        rest("SUNDAY")
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(valid3Day, "{}", 100, 300, 200L, "test-model"));

        AiWorkoutPlanPreviewResponse preview = service.generatePlan(1L, GenerateAiWorkoutPlanRequest.builder()
                .prompt("Create a 3-day/week calisthenics plan")
                .build());

        long trainingCount = preview.getSessions().stream().filter(s -> !s.isRestDay()).count();
        long restCount = preview.getSessions().stream().filter(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview::isRestDay).count();
        assertEquals(3, trainingCount);
        assertEquals(4, restCount);
        assertEquals(4, preview.getDurationWeeks());
        assertFalse(preview.getDescription().toLowerCase().contains("gemini"));
        assertTrue(preview.getSessions().stream().allMatch(s -> s.getExercises().size() <= 10));
    }

    @Test
    @DisplayName("Case B: Generate 5-day/week plan -> exactly 5 training days, <=7 days/week")
    void caseB_5DayPerWeekPlanMatchesFrequency() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(athlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(catalog);
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        GeminiStructuredWorkoutPlan valid5Day = GeminiStructuredWorkoutPlan.builder()
                .planName("5-Day PPL + Skill")
                .durationWeeks(4)
                .sessions(List.of(
                        training("MONDAY", "Push", List.of(rep(11L, "Push-Up"), rep(8L, "Parallel Bar Dips"))),
                        training("TUESDAY", "Pull", List.of(rep(13L, "Pull-Up"), rep(6L, "Muscle-Up"))),
                        training("WEDNESDAY", "Legs & Core", List.of(rep(12L, "Bodyweight Squat"), hold(19L, "L-Sit"))),
                        rest("THURSDAY"),
                        training("FRIDAY", "Planche & Push", List.of(hold(3L, "Straddle Planche"), rep(18L, "Pike Push-Up"))),
                        training("SATURDAY", "Front Lever & Pull", List.of(hold(7L, "Front Lever"), rep(10L, "Front lever pull up"))),
                        rest("SUNDAY")
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(valid5Day, "{}", 100, 300, 200L, "test-model"));

        AiWorkoutPlanPreviewResponse preview = service.generatePlan(1L, GenerateAiWorkoutPlanRequest.builder()
                .prompt("5 days/week Push Pull Legs plus skills")
                .build());

        assertEquals(7, preview.getSessions().size());
        assertEquals(5, preview.getSessions().stream().filter(s -> !s.isRestDay()).count());
        assertEquals(2, preview.getSessions().stream().filter(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview::isRestDay).count());
    }

    @Test
    @DisplayName("Case 4-Day & Schema-Independent Validation: 4 days/week accepted; 12 exercises in raw JSON rejected even without Gemini maxItems")
    void fourDayPlanAndSchemaIndependentValidation() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(athlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(catalog);
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        GeminiStructuredWorkoutPlan valid4Day = GeminiStructuredWorkoutPlan.builder()
                .planName("4-Day Upper Lower Split")
                .durationWeeks(4)
                .sessions(List.of(
                        training("MONDAY", "Upper Push/Pull", List.of(rep(11L, "Push-Up"), rep(13L, "Pull-Up"), rep(8L, "Parallel Bar Dips"), rep(18L, "Pike Push-Up"))),
                        training("TUESDAY", "Lower & Core", List.of(rep(12L, "Bodyweight Squat"), hold(19L, "L-Sit"), hold(5L, "Handstand"), rep(6L, "Muscle-Up"))),
                        rest("WEDNESDAY"),
                        training("THURSDAY", "Upper Skills", List.of(hold(1L, "Tuck Planche"), hold(7L, "Front Lever"), rep(9L, "Planche push up"), rep(10L, "Front lever pull up"))),
                        training("FRIDAY", "Lower & Conditioning", List.of(rep(12L, "Bodyweight Squat"), hold(19L, "L-Sit"), rep(11L, "Push-Up"), rep(13L, "Pull-Up"))),
                        rest("SATURDAY"),
                        rest("SUNDAY")
                ))
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(valid4Day, "{}", 100, 300, 200L, "test-model"));

        AiWorkoutPlanPreviewResponse preview = service.generatePlan(1L, GenerateAiWorkoutPlanRequest.builder()
                .prompt("Create a 4-day/week upper lower calisthenics program with 4 exercises per session")
                .build());

        assertEquals(7, preview.getSessions().size());
        assertEquals(4, preview.getSessions().stream().filter(s -> !s.isRestDay()).count());
        assertEquals(3, preview.getSessions().stream().filter(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview::isRestDay).count());
        assertTrue(preview.getSessions().stream().filter(s -> !s.isRestDay()).allMatch(s -> s.getExercises().size() == 4));

        // Even when Gemini schema has no minItems/maxItems, backend rejects >10 exercises per session
        reset(geminiApiClient);
        List<GeminiStructuredWorkoutPlan.GeminiExercise> elevenExercises = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            Exercise ex = catalog.get(i);
            elevenExercises.add(GeminiStructuredWorkoutPlan.GeminiExercise.builder()
                    .exerciseId(ex.getId())
                    .exerciseName(ex.getName())
                    .sets(3)
                    .reps(10)
                    .holdSeconds(10)
                    .restSeconds(60)
                    .build());
        }
        GeminiStructuredWorkoutPlan bloated = GeminiStructuredWorkoutPlan.builder()
                .planName("Excess Volume")
                .durationWeeks(4)
                .sessions(List.of(training("MONDAY", "Push", elevenExercises)))
                .build();
        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(bloated, "{}", 100, 300, 150L, "test-model"));

        assertThrows(AiWorkoutPlanValidationException.class, () -> service.generatePlan(1L, GenerateAiWorkoutPlanRequest.builder()
                .prompt("1-day/week push session")
                .build()));
    }

    @Test
    @DisplayName("Case C: 7-day/week plan + 14-day hallucination retry & Mapper deduplication never returns 14 days")
    void caseC_7DayPerWeekNeverDisplays14Days() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(athlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(catalog);
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());

        List<GeminiStructuredWorkoutPlan.GeminiSession> sevenUniqueDays = List.of(
                training("MONDAY", "Push", List.of(rep(11L, "Push-Up"))),
                training("TUESDAY", "Pull", List.of(rep(13L, "Pull-Up"))),
                training("WEDNESDAY", "Legs", List.of(rep(12L, "Bodyweight Squat"))),
                training("THURSDAY", "Handstand", List.of(hold(5L, "Handstand"))),
                training("FRIDAY", "Planche Push", List.of(rep(9L, "Planche push up"))),
                training("SATURDAY", "Front Lever Pull", List.of(rep(10L, "Front lever pull up"))),
                training("SUNDAY", "Core", List.of(hold(19L, "L-Sit")))
        );

        // First Gemini output wrongly returns 14 sessions (2 weeks concatenated) -> triggers 1 controlled correction retry!
        List<GeminiStructuredWorkoutPlan.GeminiSession> fourteenDays = new ArrayList<>();
        fourteenDays.addAll(sevenUniqueDays);
        fourteenDays.addAll(sevenUniqueDays);

        GeminiStructuredWorkoutPlan invalid14Day = GeminiStructuredWorkoutPlan.builder()
                .planName("14-day bug")
                .durationWeeks(2)
                .sessions(fourteenDays)
                .build();
        GeminiStructuredWorkoutPlan repaired7Day = GeminiStructuredWorkoutPlan.builder()
                .planName("7-Day Daily Calisthenics")
                .durationWeeks(2)
                .sessions(sevenUniqueDays)
                .build();

        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(invalid14Day, "{\"sessions\":14}", 100, 400, 200L, "test-model"))
                .thenReturn(new GeminiApiClient.GeminiCallResult(repaired7Day, "{\"sessions\":7}", 150, 350, 210L, "test-model"));

        AiWorkoutPlanPreviewResponse preview = service.generatePlan(1L, GenerateAiWorkoutPlanRequest.builder()
                .prompt("7-day/week training routine")
                .build());

        verify(geminiApiClient, times(2)).generateWorkoutPlan(anyString(), anyString());
        assertEquals(7, preview.getSessions().size());
        assertEquals(7, preview.getSessions().stream().filter(s -> !s.isRestDay()).count());

        // Also verify WorkoutPlanMapper deduplicates JPA bag duplicates so API response never exceeds 7 days
        WorkoutPlanMapper mapper = new WorkoutPlanMapper();
        WorkoutPlanDay mon = WorkoutPlanDay.builder().id(1L).dayOfWeek(WorkoutDayOfWeek.MONDAY).displayOrder(1).sessionType(WorkoutSessionType.PUSH).build();
        WorkoutPlanDay tue = WorkoutPlanDay.builder().id(2L).dayOfWeek(WorkoutDayOfWeek.TUESDAY).displayOrder(2).sessionType(WorkoutSessionType.PULL).build();
        WorkoutPlan plan = WorkoutPlan.builder().id(10L).name("Daily").splitType(WorkoutSplitType.CUSTOM).days(List.of(mon, mon, tue, tue)).build();
        WorkoutPlanResponse mapped = mapper.toResponse(plan);
        assertEquals(2, mapped.getDays().size());
    }

    @Test
    @DisplayName("Case D: 4-week plan distinguishes duration (4 weeks) from training days/week (e.g. 3 days/week)")
    void caseD_4WeekPlanDistinguishesWeeksFromFrequency() {
        var constraints = promptAnalyzer.analyze("Generate a 4-week plan with 3 training days/week", "INTERMEDIATE", null);
        assertEquals(4, constraints.targetDurationWeeks());
        assertTrue(constraints.requestedTrainingDaysPerWeek().isPresent());
        assertEquals(3, constraints.requestedTrainingDaysPerWeek().getAsInt());

        var durationOnlyConstraints = promptAnalyzer.analyze("Generate a 4-week calisthenics plan", "INTERMEDIATE", null);
        assertEquals(4, durationOnlyConstraints.targetDurationWeeks());
        assertTrue(durationOnlyConstraints.requestedTrainingDaysPerWeek().isEmpty());
    }

    @Test
    @DisplayName("Case E: Session classification for Nov 17/18/25/16 strictly separates Upcoming, Unfinished, Completed")
    void caseE_unfinishedVsUpcomingClassificationOnNov17() {
        LocalDate nov17 = LocalDate.of(2026, 11, 17);
        ZoneOffset utc = ZoneOffset.UTC;

        WorkoutSession nov16Completed = WorkoutSession.builder()
                .id(1L).workoutDate(LocalDate.of(2026, 11, 16)).status(WorkoutSessionStatus.COMPLETED).build();
        WorkoutSession nov17InProgress = WorkoutSession.builder()
                .id(2L).workoutDate(LocalDate.of(2026, 11, 17)).status(WorkoutSessionStatus.IN_PROGRESS).build();
        WorkoutSession nov17Scheduled = WorkoutSession.builder()
                .id(3L).workoutDate(LocalDate.of(2026, 11, 17)).status(WorkoutSessionStatus.SCHEDULED).build();
        WorkoutSession nov18IncompleteLegacy = WorkoutSession.builder()
                .id(4L).workoutDate(LocalDate.of(2026, 11, 18)).status(WorkoutSessionStatus.IN_PROGRESS).build();
        WorkoutSession nov25Scheduled = WorkoutSession.builder()
                .id(5L).workoutDate(LocalDate.of(2026, 11, 25)).status(WorkoutSessionStatus.SCHEDULED).build();

        assertEquals(WorkoutSessionClassification.COMPLETED, WorkoutSessionClassifier.classify(nov16Completed, nov17, utc));
        assertEquals(WorkoutSessionClassification.UNFINISHED, WorkoutSessionClassifier.classify(nov17InProgress, nov17, utc));
        assertEquals(WorkoutSessionClassification.UNFINISHED, WorkoutSessionClassifier.classify(nov17Scheduled, nov17, utc));
        assertEquals(WorkoutSessionClassification.UPCOMING, WorkoutSessionClassifier.classify(nov18IncompleteLegacy, nov17, utc));
        assertEquals(WorkoutSessionClassification.UPCOMING, WorkoutSessionClassifier.classify(nov25Scheduled, nov17, utc));

        assertFalse(WorkoutSessionClassifier.isUnfinished(nov18IncompleteLegacy, nov17, utc));
        assertFalse(WorkoutSessionClassifier.isUnfinished(nov25Scheduled, nov17, utc));
        assertTrue(WorkoutSessionClassifier.isUpcoming(nov18IncompleteLegacy, nov17, utc));
        assertTrue(WorkoutSessionClassifier.isUpcoming(nov25Scheduled, nov17, utc));
    }

    @Test
    @DisplayName("Case F: Duplicate exercise ID in one session fails validation; similar names remain distinct; >10 exercises rejected")
    void caseF_duplicateExerciseRejectedAndSimilarNamesPreserved() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(athlete));
        when(exerciseRepository.findByActiveTrue()).thenReturn(catalog);

        // Similar names (Tuck Planche id=1, Advanced Tuck Planche id=2, Straddle Planche id=3, Planche id=16) MUST remain distinct!
        GeminiStructuredWorkoutPlan distinctSimilarPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("Planche Progressions")
                .durationWeeks(4)
                .sessions(List.of(
                        training("MONDAY", "Planche Ladder", List.of(
                                hold(1L, "Tuck Planche"),
                                hold(2L, "Advanced Tuck Planche"),
                                hold(3L, "Straddle Planche"),
                                hold(16L, "Planche")
                        ))
                ))
                .build();
        when(workoutPlanRepository.findFirstByUserIdAndActiveTrueOrderByUpdatedAtDesc(1L)).thenReturn(Optional.empty());
        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(distinctSimilarPlan, "{}", 100, 200, 100L, "test-model"));

        AiWorkoutPlanPreviewResponse distinctPreview = service.generatePlan(1L, GenerateAiWorkoutPlanRequest.builder()
                .prompt("Planche specialization")
                .build());
        assertEquals(4, distinctPreview.getSessions().get(0).getExercises().size());
        assertThat(distinctPreview.getSessions().get(0).getExercises())
                .extracting(AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview::getExerciseId)
                .containsExactly(1L, 2L, 3L, 16L);

        // Same exercise ID repeated in same session -> rejected after 1 controlled retry!
        reset(geminiApiClient);
        GeminiStructuredWorkoutPlan duplicateExPlan = GeminiStructuredWorkoutPlan.builder()
                .planName("Duplicate Push-Up")
                .durationWeeks(4)
                .sessions(List.of(
                        training("MONDAY", "Push", List.of(rep(11L, "Push-Up"), rep(11L, "Push-Up")))
                ))
                .build();
        when(geminiApiClient.generateWorkoutPlan(anyString(), anyString()))
                .thenReturn(new GeminiApiClient.GeminiCallResult(duplicateExPlan, "{}", 100, 200, 100L, "test-model"));

        assertThrows(AiWorkoutPlanValidationException.class, () -> service.generatePlan(1L, GenerateAiWorkoutPlanRequest.builder()
                .prompt("Push workout")
                .build()));
        verify(geminiApiClient, times(2)).generateWorkoutPlan(anyString(), anyString());

        // >10 exercises in a single session (e.g., 12) -> commit validation also rejects cleanly!
        List<AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview> twelveExercises = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            twelveExercises.add(AiWorkoutPlanPreviewResponse.AiWorkoutExercisePreview.builder()
                    .exerciseId(catalog.get(i).getId())
                    .exerciseName(catalog.get(i).getName())
                    .sets(3)
                    .reps(10)
                    .restSeconds(60)
                    .build());
        }
        AiWorkoutPlanPreviewResponse bloatedPreview = AiWorkoutPlanPreviewResponse.builder()
                .planName("Bloated Plan")
                .durationWeeks(4)
                .startDate(LocalDate.of(2026, 11, 18))
                .sessions(List.of(AiWorkoutPlanPreviewResponse.AiWorkoutSessionPreview.builder()
                        .dayOfWeek(WorkoutDayOfWeek.MONDAY).title("Bloated").restDay(false).exercises(twelveExercises).build()))
                .build();
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(athlete));
        assertThrows(AiWorkoutPlanValidationException.class, () -> service.commitPlan(1L,
                CommitAiWorkoutPlanRequest.builder().plan(bloatedPreview).replaceExisting(false).build()));
    }

    private Exercise ex(Long id, String name, String code, ExerciseCategory cat) {
        return Exercise.builder().id(id).name(name).code(code).category(cat).difficulty(DifficultyLevel.INTERMEDIATE).active(true).build();
    }

    private GeminiStructuredWorkoutPlan.GeminiSession training(String dow, String title, List<GeminiStructuredWorkoutPlan.GeminiExercise> list) {
        return GeminiStructuredWorkoutPlan.GeminiSession.builder().dayOfWeek(dow).title(title).restDay(false).exercises(list).build();
    }

    private GeminiStructuredWorkoutPlan.GeminiSession rest(String dow) {
        return GeminiStructuredWorkoutPlan.GeminiSession.builder().dayOfWeek(dow).title("Rest Day").restDay(true).exercises(Collections.emptyList()).build();
    }

    private GeminiStructuredWorkoutPlan.GeminiExercise rep(Long id, String name) {
        return GeminiStructuredWorkoutPlan.GeminiExercise.builder().exerciseId(id).exerciseName(name).sets(3).reps(10).restSeconds(90).build();
    }

    private GeminiStructuredWorkoutPlan.GeminiExercise hold(Long id, String name) {
        return GeminiStructuredWorkoutPlan.GeminiExercise.builder().exerciseId(id).exerciseName(name).sets(3).holdSeconds(15).restSeconds(120).build();
    }
}
