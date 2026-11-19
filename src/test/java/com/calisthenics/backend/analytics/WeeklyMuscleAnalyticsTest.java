package com.calisthenics.backend.analytics;

import com.calisthenics.backend.analytics.dto.ExerciseProgressResponse;
import com.calisthenics.backend.analytics.dto.MuscleVolumeDistributionDto;
import com.calisthenics.backend.analytics.dto.WeeklyAnalyticsResponse;
import com.calisthenics.backend.analytics.dto.WeeklyMuscleAnalyticsResponse;
import com.calisthenics.backend.analytics.repository.PersonalRecordRepository;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.analytics.service.TrainingAnalyticsService;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.config.AppProperties;
import com.calisthenics.backend.config.UserTimeResolver;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseRepository;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workout.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WeeklyMuscleAnalyticsTest {

    @Mock
    private WorkoutSessionRepository workoutSessionRepository;
    @Mock
    private PersonalRecordRepository personalRecordRepository;
    @Mock
    private ExerciseRepository exerciseRepository;
    @Mock
    private CoachClientRelationshipRepository coachClientRelationshipRepository;
    @Mock
    private PersonalRecordService personalRecordService;

    private TrainingAnalyticsService analyticsService;
    private User athlete;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-19T12:00:00Z"), ZoneOffset.UTC); // Thursday
        UserTimeResolver timeResolver = new UserTimeResolver(new AppProperties(), fixedClock);
        analyticsService = new TrainingAnalyticsService(
                workoutSessionRepository,
                personalRecordRepository,
                exerciseRepository,
                coachClientRelationshipRepository,
                personalRecordService,
                timeResolver,
                new MuscleTaxonomyService()
        );
        athlete = User.builder().id(1L).email("athlete@calisthenics.ai").fullName("Athlete").role(Role.ROLE_USER).build();
    }

    @Test
    void getWeeklyMuscleAnalytics_ComputesPrimaryAndSecondaryWeightsAndThresholds() {
        Exercise pushUp = Exercise.builder()
                .id(1L).name("Push-Up").code("PUSH_UP")
                .category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.BEGINNER)
                .build();
        Exercise squat = Exercise.builder()
                .id(2L).name("Bodyweight Squat").code("SQUAT")
                .category(ExerciseCategory.LEGS).difficulty(DifficultyLevel.BEGINNER)
                .build();

        // Week for 2026-11-19 is Monday 2026-11-16 .. Sunday 2026-11-22
        LocalDate monday = LocalDate.of(2026, 11, 16);
        LocalDate sunday = LocalDate.of(2026, 11, 22);

        // Session 1: COMPLETED with 8 completed Push-Up sets (chest = 8.0 OPTIMAL) and 1 incomplete set (ignored)
        WorkoutSession completedPushSession = WorkoutSession.builder()
                .id(10L).user(athlete).title("Push").workoutDate(LocalDate.of(2026, 11, 17))
                .status(WorkoutSessionStatus.COMPLETED).build();
        WorkoutSessionExercise pushEx = WorkoutSessionExercise.builder()
                .id(100L).workoutSession(completedPushSession).exercise(pushUp).exerciseNameSnapshot("Push-Up")
                .orderIndex(1).targetSets(9).restSeconds(60).build();
        List<WorkoutSessionSet> pushSets = new ArrayList<>();
        for (int i = 1; i <= 8; i++) {
            pushSets.add(WorkoutSessionSet.builder()
                    .workoutSessionExercise(pushEx).setNumber(i).reps(10).weightKg(BigDecimal.valueOf(20.0))
                    .completed(true).build());
        }
        pushSets.add(WorkoutSessionSet.builder()
                .workoutSessionExercise(pushEx).setNumber(9).reps(99).weightKg(BigDecimal.valueOf(50.0))
                .completed(false).build());
        pushEx.setSets(pushSets);
        completedPushSession.setExercises(List.of(pushEx));

        // Session 2: COMPLETED with 23 completed Squat sets (quads/glutes = 23.0 OVERLOADED)
        WorkoutSession completedSquatSession = WorkoutSession.builder()
                .id(11L).user(athlete).title("Legs").workoutDate(LocalDate.of(2026, 11, 18))
                .status(WorkoutSessionStatus.COMPLETED).build();
        WorkoutSessionExercise squatEx = WorkoutSessionExercise.builder()
                .id(101L).workoutSession(completedSquatSession).exercise(squat).exerciseNameSnapshot("Squat")
                .orderIndex(1).targetSets(23).restSeconds(90).build();
        List<WorkoutSessionSet> squatSets = new ArrayList<>();
        for (int i = 1; i <= 23; i++) {
            squatSets.add(WorkoutSessionSet.builder()
                    .workoutSessionExercise(squatEx).setNumber(i).reps(12)
                    .completed(true).build());
        }
        squatEx.setSets(squatSets);
        completedSquatSession.setExercises(List.of(squatEx));

        // Session 3: IN_PROGRESS (must be ignored defensively)
        WorkoutSession inProgressSession = WorkoutSession.builder()
                .id(12L).user(athlete).title("Draft").workoutDate(LocalDate.of(2026, 11, 19))
                .status(WorkoutSessionStatus.IN_PROGRESS).build();
        WorkoutSessionExercise ignoredEx = WorkoutSessionExercise.builder()
                .id(102L).workoutSession(inProgressSession).exercise(pushUp).exerciseNameSnapshot("Push-Up")
                .orderIndex(1).targetSets(5).restSeconds(60).build();
        ignoredEx.setSets(List.of(WorkoutSessionSet.builder()
                .workoutSessionExercise(ignoredEx).setNumber(1).reps(10).completed(true).build()));
        inProgressSession.setExercises(List.of(ignoredEx));

        when(workoutSessionRepository.findCompletedSessionsBetweenDates(
                eq(1L), eq(WorkoutSessionStatus.COMPLETED), eq(monday), eq(sunday)))
                .thenReturn(List.of(completedPushSession, completedSquatSession, inProgressSession));

        WeeklyMuscleAnalyticsResponse result = analyticsService.getWeeklyMuscleAnalytics(1L, 0, "UTC");

        assertThat(result.getStartDate()).isEqualTo("2026-11-16");
        assertThat(result.getEndDate()).isEqualTo("2026-11-22");
        assertThat(result.getTotalCompletedWorkouts()).isEqualTo(2);
        assertThat(result.getTotalCompletedSets()).isEqualTo(31);
        assertThat(result.getMuscles()).hasSize(22);

        Map<String, MuscleVolumeDistributionDto> byId = result.getMuscles().stream()
                .collect(Collectors.toMap(MuscleVolumeDistributionDto::getMuscleId, Function.identity()));

        // Chest: 8 primary sets -> 8.0 effectiveSets, OPTIMAL, intensity = 0.5, volume = 8 * 10 * 20 = 1600.0
        MuscleVolumeDistributionDto chest = byId.get("chest");
        assertThat(chest.getPrimarySets()).isEqualTo(8);
        assertThat(chest.getSecondarySets()).isEqualTo(0);
        assertThat(chest.getEffectiveSets()).isEqualTo(8.0);
        assertThat(chest.getPrimaryReps()).isEqualTo(80);
        assertThat(chest.getPrimaryVolumeKg()).isEqualTo(1600.0);
        assertThat(chest.getActivationIntensity()).isEqualTo(0.5);
        assertThat(chest.getTrainingStatus()).isEqualTo("OPTIMAL");

        // Front Deltoids & Triceps: 8 secondary sets from Push-Up -> 4.0 effectiveSets, LOW, secondaryVolume = 1600.0
        MuscleVolumeDistributionDto triceps = byId.get("triceps");
        assertThat(triceps.getPrimarySets()).isEqualTo(0);
        assertThat(triceps.getSecondarySets()).isEqualTo(8);
        assertThat(triceps.getEffectiveSets()).isEqualTo(4.0);
        assertThat(triceps.getSecondaryReps()).isEqualTo(80);
        assertThat(triceps.getSecondaryVolumeKg()).isEqualTo(1600.0);
        assertThat(triceps.getActivationIntensity()).isEqualTo(0.25);
        assertThat(triceps.getTrainingStatus()).isEqualTo("LOW");

        // Quadriceps & Glutes: 23 primary sets -> 23.0 effectiveSets, OVERLOADED, intensity capped at 1.0
        MuscleVolumeDistributionDto quads = byId.get("quadriceps");
        assertThat(quads.getEffectiveSets()).isEqualTo(23.0);
        assertThat(quads.getActivationIntensity()).isEqualTo(1.0);
        assertThat(quads.getTrainingStatus()).isEqualTo("OVERLOADED");

        // Rectus Abdominis: 8 secondary (Push-Up) + 23 secondary (Squat) = 31 secondary -> 15.5 effectiveSets (OPTIMAL)
        MuscleVolumeDistributionDto abs = byId.get("rectus_abdominis");
        assertThat(abs.getSecondarySets()).isEqualTo(31);
        assertThat(abs.getEffectiveSets()).isEqualTo(15.5);
        assertThat(abs.getTrainingStatus()).isEqualTo("OPTIMAL");

        // Biceps: 0 sets -> UNDERTRAINED, intensity = 0.0
        MuscleVolumeDistributionDto biceps = byId.get("biceps");
        assertThat(biceps.getEffectiveSets()).isEqualTo(0.0);
        assertThat(biceps.getActivationIntensity()).isEqualTo(0.0);
        assertThat(biceps.getTrainingStatus()).isEqualTo("UNDERTRAINED");

        assertThat(result.getOptimalMuscles()).contains("chest", "rectus_abdominis");
        assertThat(result.getOverloadedMuscles()).containsExactlyInAnyOrder("glutes", "quadriceps");
        assertThat(result.getUndertrainedMuscles()).contains("biceps");

        double regionSum = result.getRegionDistribution().values().stream().mapToDouble(Double::doubleValue).sum();
        assertThat(regionSum).isEqualTo(100.0);
        assertThat(result.getCaveats()).isNotEmpty();
    }

    @Test
    void getWeeklyMuscleAnalytics_HandlesEmptyWeekAndIsometricHoldDuration() {
        // Previous week (weekOffset = -1): Monday 2026-11-09 .. Sunday 2026-11-15
        LocalDate prevMon = LocalDate.of(2026, 11, 9);
        LocalDate prevSun = LocalDate.of(2026, 11, 15);
        when(workoutSessionRepository.findCompletedSessionsBetweenDates(
                eq(1L), eq(WorkoutSessionStatus.COMPLETED), eq(prevMon), eq(prevSun)))
                .thenReturn(List.of());

        WeeklyMuscleAnalyticsResponse emptyWeek = analyticsService.getWeeklyMuscleAnalytics(1L, -1, "UTC");

        assertThat(emptyWeek.getStartDate()).isEqualTo("2026-11-09");
        assertThat(emptyWeek.getEndDate()).isEqualTo("2026-11-15");
        assertThat(emptyWeek.getTotalCompletedWorkouts()).isEqualTo(0);
        assertThat(emptyWeek.getTotalEffectiveSets()).isEqualTo(0.0);
        assertThat(emptyWeek.getMuscles()).hasSize(22);
        assertThat(emptyWeek.getRegionDistribution())
                .containsEntry("UPPER_BODY", 0.0)
                .containsEntry("CORE", 0.0)
                .containsEntry("LOWER_BODY", 0.0);
        assertThat(emptyWeek.getCaveats().getFirst()).contains("No completed workouts recorded");

        // Isometric hold with 1 set checks minimum 0.18 activation floor and hold seconds tracking
        Exercise frontLever = Exercise.builder()
                .id(3L).name("Front Lever").code("FRONT_LEVER")
                .category(ExerciseCategory.SKILL).difficulty(DifficultyLevel.ADVANCED)
                .build();
        WorkoutSession holdSession = WorkoutSession.builder()
                .id(20L).user(athlete).title("Skill").workoutDate(LocalDate.of(2026, 11, 18))
                .status(WorkoutSessionStatus.COMPLETED).build();
        WorkoutSessionExercise holdEx = WorkoutSessionExercise.builder()
                .id(200L).workoutSession(holdSession).exercise(frontLever).exerciseNameSnapshot("Front Lever")
                .orderIndex(1).targetSets(1).restSeconds(120).build();
        holdEx.setSets(List.of(WorkoutSessionSet.builder()
                .workoutSessionExercise(holdEx).setNumber(1).holdDurationSeconds(15).completed(true).build()));
        holdSession.setExercises(List.of(holdEx));

        LocalDate currMon = LocalDate.of(2026, 11, 16);
        LocalDate currSun = LocalDate.of(2026, 11, 22);
        when(workoutSessionRepository.findCompletedSessionsBetweenDates(
                eq(1L), eq(WorkoutSessionStatus.COMPLETED), eq(currMon), eq(currSun)))
                .thenReturn(List.of(holdSession));

        WeeklyMuscleAnalyticsResponse holdResult = analyticsService.getWeeklyMuscleAnalytics(1L, 0, "UTC");
        MuscleVolumeDistributionDto lats = holdResult.getMuscles().stream()
                .filter(m -> m.getMuscleId().equals("latissimus_dorsi"))
                .findFirst()
                .orElseThrow();

        assertThat(lats.getPrimaryHoldSeconds()).isEqualTo(15);
        assertThat(lats.getEffectiveSets()).isEqualTo(1.0);
        assertThat(lats.getActivationIntensity()).isEqualTo(0.18); // minimum non-zero floor
        assertThat(lats.getTrainingStatus()).isEqualTo("UNDERTRAINED");
    }

    @Test
    void getWeeklyAnalyticsAndExerciseProgress_IncludeMuscleMetadata() {
        Exercise pushUp = Exercise.builder()
                .id(1L).name("Push-Up").code("PUSH_UP")
                .category(ExerciseCategory.PUSH).difficulty(DifficultyLevel.BEGINNER)
                .description("Horizontal push").instructions("Lower chest to floor")
                .targetMuscles("Chest").secondaryMuscles("Triceps")
                .build();

        when(exerciseRepository.findById(1L)).thenReturn(java.util.Optional.of(pushUp));
        when(workoutSessionRepository.findCompletedSessionsByExercise(1L, WorkoutSessionStatus.COMPLETED, 1L))
                .thenReturn(List.of());
        when(personalRecordRepository.findByUserIdAndExerciseIdOrderByAchievedAtDesc(1L, 1L))
                .thenReturn(List.of());

        ExerciseProgressResponse progress = analyticsService.getExerciseProgress(1L, 1L, null, "UTC");
        assertThat(progress.getDifficulty()).isEqualTo("BEGINNER");
        assertThat(progress.getDescription()).isEqualTo("Horizontal push");
        assertThat(progress.getInstructions()).isEqualTo("Lower chest to floor");
        assertThat(progress.getPrimaryMuscles()).containsExactly("chest");
        assertThat(progress.getSecondaryMuscleIds()).contains("front_deltoids", "triceps");
        assertThat(progress.getMuscleTargets()).isNotEmpty();

        LocalDate currMon = LocalDate.of(2026, 11, 16);
        LocalDate currSun = LocalDate.of(2026, 11, 22);
        when(workoutSessionRepository.findCompletedSessionsBetweenDates(
                eq(1L), eq(WorkoutSessionStatus.COMPLETED), eq(currMon), eq(currSun)))
                .thenReturn(List.of());
        when(workoutSessionRepository.findCompletedSessionsBetweenDates(
                eq(1L), eq(WorkoutSessionStatus.COMPLETED), eq(currMon.minusWeeks(1)), eq(currMon.minusDays(1))))
                .thenReturn(List.of());

        WeeklyAnalyticsResponse weekly = analyticsService.getWeeklyAnalytics(1L, "UTC");
        assertThat(weekly.getMuscleMap()).isNotNull();
        assertThat(weekly.getMuscleMap().getMuscles()).hasSize(22);
    }
}
