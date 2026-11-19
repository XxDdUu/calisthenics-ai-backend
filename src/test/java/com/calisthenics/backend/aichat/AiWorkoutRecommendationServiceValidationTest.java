package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.GeminiAssistantOutput;
import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationItemDto;
import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationsCardDto;
import com.calisthenics.backend.aichat.entity.AiMessage;
import com.calisthenics.backend.aichat.service.AiWorkoutRecommendationService;
import com.calisthenics.backend.aichat.service.ExerciseDeduplicator;
import com.calisthenics.backend.aiworkout.gemini.GeminiApiClient;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.repository.WorkoutPlanAssignmentRepository;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.workout.WorkoutSessionRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanDayRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanExerciseRepository;
import com.calisthenics.backend.workoutplan.repository.WorkoutPlanRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiWorkoutRecommendationServiceValidationTest {

    @Mock private ExerciseRepository exerciseRepository;
    @Mock private WorkoutPlanRepository workoutPlanRepository;
    @Mock private WorkoutPlanDayRepository workoutPlanDayRepository;
    @Mock private WorkoutPlanExerciseRepository workoutPlanExerciseRepository;
    @Mock private WorkoutPlanAssignmentRepository workoutPlanAssignmentRepository;
    @Mock private CoachClientRelationshipRepository coachClientRelationshipRepository;
    @Mock private ClientProfileRepository clientProfileRepository;
    @Mock private PersonalRecordRepository personalRecordRepository;
    @Mock private WorkoutSessionRepository workoutSessionRepository;

    private AiWorkoutRecommendationService service;

    @BeforeEach
    void setUp() {
        service = new AiWorkoutRecommendationService(
                exerciseRepository,
                new MuscleTaxonomyService(),
                workoutPlanRepository,
                workoutPlanDayRepository,
                workoutPlanExerciseRepository,
                workoutPlanAssignmentRepository,
                coachClientRelationshipRepository,
                clientProfileRepository,
                personalRecordRepository,
                workoutSessionRepository,
                new ObjectMapper()
        );
    }

    @Test
    void validateAndSanitizeCard_dropsInventedIdsAndNormalizesNullCollections() {
        Exercise validEx = Exercise.builder()
                .id(42L)
                .name("Bulgarian Split Squat")
                .code("BULGARIAN_SPLIT_SQUAT")
                .category(ExerciseCategory.LEGS)
                .difficulty(DifficultyLevel.INTERMEDIATE)
                .targetMuscles("Quadriceps, Glutes")
                .status(ExerciseStatus.PUBLISHED)
                .origin(ExerciseOrigin.MANUAL)
                .active(true)
                .build();

        ExerciseRecommendationsCardDto raw = new ExerciseRecommendationsCardDto();
        raw.setRecommendations(List.of(
                ExerciseRecommendationItemDto.builder()
                        .exerciseId(999999L)
                        .name("Hallucinated Movement")
                        .build(),
                ExerciseRecommendationItemDto.builder()
                        .exerciseId(-5L)
                        .name("Negative ID Movement")
                        .build(),
                ExerciseRecommendationItemDto.builder()
                        .exerciseId(42L)
                        .name("Bulgarian Split Squat")
                        .primaryMuscleIds(null)
                        .secondaryMuscleIds(null)
                        .fillsUndertrainedMuscles(null)
                        .alreadyInPlanDays(null)
                        .build()
        ));
        raw.setUndertrainedMuscles(null);
        raw.setAvailablePlanDays(null);
        raw.setPlanWarnings(null);

        ExerciseRecommendationsCardDto cleaned = service.validateAndSanitizeCard(raw, Map.of(42L, validEx));

        assertThat(cleaned.getUndertrainedMuscles()).isEmpty();
        assertThat(cleaned.getAvailablePlanDays()).isEmpty();
        assertThat(cleaned.getPlanWarnings()).isEmpty();
        assertThat(cleaned.getRecommendations()).hasSize(1);
        ExerciseRecommendationItemDto safeItem = cleaned.getRecommendations().get(0);
        assertThat(safeItem.getExerciseId()).isEqualTo(42L);
        assertThat(safeItem.getPrimaryMuscleIds()).isNotEmpty();
        assertThat(safeItem.getSecondaryMuscleIds()).isNotNull();
        assertThat(safeItem.getRecommendedSets()).isEqualTo(3);
        assertThat(safeItem.getPrescriptionSummary()).contains("3 sets");
    }

    @Test
    void recommendExercises_handlesEmptyCatalogWithoutCrashing() {
        when(exerciseRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of());

        AiWorkoutRecommendationService.RecommendationOutcome out =
                service.recommendExercises(1L, "Recommend leg exercises", null, false);

        assertThat(out.intent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(out.card().getRecommendations()).isEmpty();
        assertThat(out.reply()).contains("No published exercises");
        verifyNoInteractions(workoutPlanRepository);
    }

    @Test
    void exerciseDeduplicator_stripsAbbreviationsAliasesAndSetRepSchemes() {
        assertThat(ExerciseDeduplicator.normalizeName("DB Bulgarian Split Squat"))
                .isEqualTo(ExerciseDeduplicator.normalizeName("Dumbbell Bulgarian Split Squat"));
        assertThat(ExerciseDeduplicator.normalizeName("Single-Leg RDL (4x8)"))
                .isEqualTo(ExerciseDeduplicator.normalizeName("Single-Leg Romanian Deadlift — 3 sets × 10 reps"));
        assertThat(ExerciseDeduplicator.normalizeName("HSPU"))
                .isEqualTo(ExerciseDeduplicator.normalizeName("Handstand Push-Ups"));

        Exercise bss = Exercise.builder().id(10L).name("Bulgarian Split Squat").code("BULGARIAN_SPLIT_SQUAT").aliases("Rear Foot Elevated Split Squat").build();
        List<String> filtered = service.filterUnseenExerciseNames(
                List.of("Bulgarian Split Squat", "DB Bulgarian Split Squat", "Rear Foot Elevated Split Squat", "Walking Lunges"),
                Set.of(10L),
                List.of("Dumbbell Bulgarian Split Squat"),
                List.of(bss)
        );
        assertThat(filtered).containsExactly("Walking Lunges");
    }

    @Test
    void listExercises_filtersGeminiDuplicatesAndPassesExclusionsToGemini() {
        Exercise squat = Exercise.builder()
                .id(1L).name("Bodyweight Squat").code("SQUAT")
                .category(ExerciseCategory.LEGS).difficulty(DifficultyLevel.BEGINNER)
                .targetMuscles("Quadriceps, Glutes").status(ExerciseStatus.PUBLISHED).active(true).build();
        when(exerciseRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(squat));

        GeminiApiClient mockGemini = mock(GeminiApiClient.class);
        ReflectionTestUtils.setField(service, "geminiApiClient", mockGemini);
        when(mockGemini.callAssistant(anyString(), anyList())).thenReturn(Optional.of(
                GeminiAssistantOutput.builder()
                        .intent("LIST_EXERCISES")
                        .reply("1. Bodyweight Squat\n2. DB Bulgarian Split Squat\n3. Romanian Deadlift")
                        .build()
        ));

        AiMessage prevAssistant = AiMessage.builder()
                .role("assistant")
                .intent("LIST_EXERCISES")
                .metadata("{\"type\":\"EXERCISE_LIST\",\"criteriaKey\":\"LEGS\",\"usedSupplementalAi\":true,\"exercises\":[{\"exerciseId\":1,\"name\":\"Bodyweight Squat\"},{\"name\":\"Dumbbell Bulgarian Split Squat\"}]}")
                .content("**Leg Exercises**\n1. Bodyweight Squat\n2. Dumbbell Bulgarian Split Squat")
                .build();

        AiWorkoutRecommendationService.RecommendationOutcome outcome =
                service.listExercises(1L, "Another exercise", List.of(prevAssistant), true, false);

        assertThat(outcome.intent()).isEqualTo("LIST_EXERCISES");
        assertThat(outcome.reply()).contains("Romanian Deadlift");
        assertThat(outcome.reply()).doesNotContain("Bodyweight Squat");
        assertThat(outcome.reply()).doesNotContain("Bulgarian Split Squat");
        verify(mockGemini).callAssistant(
                org.mockito.ArgumentMatchers.argThat(sys -> sys.contains("Dumbbell Bulgarian Split Squat") && sys.contains("Bodyweight Squat")),
                anyList()
        );
        verifyNoInteractions(workoutPlanRepository);
    }

    @Test
    void recommendExercises_informationalOnlyOmitsPlanAnalysisGapsAndWeekdays() {
        Exercise pike = Exercise.builder()
                .id(20L).name("Pike Push-Up").code("PIKE_PUSH_UP")
                .category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.INTERMEDIATE)
                .description("Vertical overhead pressing progression toward handstand push-ups.")
                .targetMuscles("Front Deltoids, Triceps").status(ExerciseStatus.PUBLISHED).active(true).build();
        when(exerciseRepository.findByActiveTrueOrderByNameAsc()).thenReturn(List.of(pike));

        AiWorkoutRecommendationService.RecommendationOutcome out = service.recommendExercises(
                1L, "Recommend some exercises for handstand push-ups", WorkoutDayOfWeek.MONDAY, false
        );

        assertThat(out.intent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(out.card().isInformationalOnly()).isTrue();
        assertThat(out.card().getUndertrainedMuscles()).isEmpty();
        assertThat(out.card().getAvailablePlanDays()).isEmpty();
        assertThat(out.card().getSuggestedDayOfWeek()).isNull();
        assertThat(out.reply()).contains("Pike Push-Up");
        assertThat(out.reply()).doesNotContain("Current Plan Analyzed", "Gaps Detected", "effective", "Monday", "weekday");
        verifyNoInteractions(workoutPlanRepository);
    }
}
