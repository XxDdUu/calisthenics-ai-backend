package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationItemDto;
import com.calisthenics.backend.aichat.dto.recommendation.ExerciseRecommendationsCardDto;
import com.calisthenics.backend.aichat.service.AiWorkoutRecommendationService;
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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
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
        when(workoutPlanRepository.findByUserIdOrderByActiveDescCreatedAtDesc(1L)).thenReturn(List.of());

        AiWorkoutRecommendationService.RecommendationOutcome out =
                service.recommendExercises(1L, "Recommend leg exercises", null, false);

        assertThat(out.intent()).isEqualTo("EXERCISE_RECOMMENDATION");
        assertThat(out.card().getRecommendations()).isEmpty();
        assertThat(out.reply()).contains("No published exercises");
    }
}
