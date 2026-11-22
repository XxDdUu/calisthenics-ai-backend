package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.service.WorkoutPlanContextService;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.muscle.MuscleTaxonomyService;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanExercise;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkoutPlanContextServiceTest {

    private WorkoutPlanContextService service;
    private Exercise pushUp;
    private Exercise pullUp;
    private Exercise frontLever;
    private Exercise frontLeverRaises;
    private Exercise tuckPlanche;
    private Exercise pistolSquat;

    @BeforeEach
    void setUp() {
        service = new WorkoutPlanContextService(null, null, null, null, null, new MuscleTaxonomyService());
        pushUp = Exercise.builder().id(1L).name("Push-Up").code("PUSH_UP").category(ExerciseCategory.PUSH)
                .difficulty(DifficultyLevel.BEGINNER).status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();
        pullUp = Exercise.builder().id(2L).name("Pull-Up").code("PULL_UP").category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.INTERMEDIATE).status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();
        frontLever = Exercise.builder().id(3L).name("Front Lever").code("FRONT_LEVER").category(ExerciseCategory.SKILL)
                .difficulty(DifficultyLevel.ADVANCED).status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();
        frontLeverRaises = Exercise.builder().id(4L).name("Front Lever Raises").code("FRONT_LEVER_RAISES").category(ExerciseCategory.PULL)
                .difficulty(DifficultyLevel.INTERMEDIATE).status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();
        tuckPlanche = Exercise.builder().id(5L).name("Tuck Planche").code("TUCK_PLANCHE").category(ExerciseCategory.ISOMETRIC)
                .difficulty(DifficultyLevel.INTERMEDIATE).status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();
        pistolSquat = Exercise.builder().id(6L).name("Pistol Squat").code("PISTOL_SQUAT").category(ExerciseCategory.LEGS)
                .difficulty(DifficultyLevel.INTERMEDIATE).status(ExerciseStatus.PUBLISHED).origin(ExerciseOrigin.MANUAL).active(true).build();
    }

    @Test
    @DisplayName("extractSemanticDayQuery recognizes pushing/pull/Front Lever/Planche/leg day phrases without hijacking routine phrases")
    void extractSemanticDayQuery_parsesFocusCorrectly() {
        assertThat(WorkoutPlanContextService.extractSemanticDayQuery("My pull day.")).isEqualTo("pull");
        assertThat(WorkoutPlanContextService.extractSemanticDayQuery("My pushing day")).isEqualTo("push");
        assertThat(WorkoutPlanContextService.extractSemanticDayQuery("Add Front Lever exercises to my Front Lever day"))
                .isEqualTo("front lever");
        assertThat(WorkoutPlanContextService.extractSemanticDayQuery("My Planche day")).isEqualTo("planche");
        assertThat(WorkoutPlanContextService.extractSemanticDayQuery("My leg day")).isEqualTo("leg");
        assertThat(WorkoutPlanContextService.extractSemanticDayQuery("Add some Front Lever exercises to my routine.")).isNull();
        assertThat(WorkoutPlanContextService.extractSemanticDayQuery("Tuesday")).isNull();
    }

    @Test
    @DisplayName("findMatchingDays matches unlabeled CUSTOM days purely by actual assigned exercises and muscles, never matching Rest Days")
    void findMatchingDays_infersFromActualExercisesWhenDayNamesAreGeneric() {
        WorkoutPlan plan = WorkoutPlan.builder().id(10L).name("Unlabeled Split").splitType(WorkoutSplitType.CUSTOM).active(true).build();
        WorkoutPlanDay mon = WorkoutPlanDay.builder().id(101L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.MONDAY)
                .displayOrder(1).name("Day 1").sessionType(WorkoutSessionType.CUSTOM).restDay(false).build();
        mon.getExercises().add(WorkoutPlanExercise.builder().id(1L).workoutPlanDay(mon).exercise(pushUp).sets(4).reps(12).restSeconds(90).displayOrder(1).build());
        mon.getExercises().add(WorkoutPlanExercise.builder().id(2L).workoutPlanDay(mon).exercise(tuckPlanche).sets(3).durationSeconds(15).restSeconds(90).displayOrder(2).build());

        WorkoutPlanDay thu = WorkoutPlanDay.builder().id(104L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.THURSDAY)
                .displayOrder(4).name("Day 4").sessionType(WorkoutSessionType.CUSTOM).restDay(false).build();
        thu.getExercises().add(WorkoutPlanExercise.builder().id(3L).workoutPlanDay(thu).exercise(pullUp).sets(4).reps(8).restSeconds(120).displayOrder(1).build());
        thu.getExercises().add(WorkoutPlanExercise.builder().id(4L).workoutPlanDay(thu).exercise(frontLever).sets(3).durationSeconds(12).restSeconds(120).displayOrder(2).build());

        WorkoutPlanDay sat = WorkoutPlanDay.builder().id(106L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.SATURDAY)
                .displayOrder(6).name("Day 6").sessionType(WorkoutSessionType.CUSTOM).restDay(false).build();
        sat.getExercises().add(WorkoutPlanExercise.builder().id(5L).workoutPlanDay(sat).exercise(pistolSquat).sets(4).reps(8).restSeconds(90).displayOrder(1).build());

        WorkoutPlanDay sun = WorkoutPlanDay.builder().id(107L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.SUNDAY)
                .displayOrder(7).name("Rest Day").sessionType(WorkoutSessionType.REST).restDay(true).build();

        plan.getDays().addAll(List.of(mon, thu, sat, sun));

        WorkoutPlanContextService.ActiveWorkoutPlanContext ctx = service.buildPlanContext(plan, plan.getDays(), false, null);

        assertThat(service.findMatchingDays(ctx, "pull"))
                .extracting(WorkoutPlanContextService.DayContext::dayOfWeek)
                .containsExactly(WorkoutDayOfWeek.THURSDAY);
        assertThat(service.findMatchingDays(ctx, "push"))
                .extracting(WorkoutPlanContextService.DayContext::dayOfWeek)
                .containsExactly(WorkoutDayOfWeek.MONDAY);
        assertThat(service.findMatchingDays(ctx, "planche"))
                .extracting(WorkoutPlanContextService.DayContext::dayOfWeek)
                .containsExactly(WorkoutDayOfWeek.MONDAY);
        assertThat(service.findMatchingDays(ctx, "front lever"))
                .extracting(WorkoutPlanContextService.DayContext::dayOfWeek)
                .containsExactly(WorkoutDayOfWeek.THURSDAY);
        assertThat(service.findMatchingDays(ctx, "leg"))
                .extracting(WorkoutPlanContextService.DayContext::dayOfWeek)
                .containsExactly(WorkoutDayOfWeek.SATURDAY);
    }

    @Test
    @DisplayName("evaluateDaysForAddition penalizes high-volume days and never recommends Rest Days")
    void evaluateDaysForAddition_prefersManageableAlignedDayAndExcludesRestDays() {
        WorkoutPlan plan = WorkoutPlan.builder().id(20L).name("Volume Aware Split").splitType(WorkoutSplitType.PPL).active(true).build();

        WorkoutPlanDay tueHeavyPull = WorkoutPlanDay.builder().id(202L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.TUESDAY)
                .displayOrder(2).name("Pull (Hard)").sessionType(WorkoutSessionType.PULL).restDay(false).build();
        for (int i = 1; i <= 5; i++) {
            tueHeavyPull.getExercises().add(WorkoutPlanExercise.builder()
                    .id((long) i).workoutPlanDay(tueHeavyPull).exercise(i == 1 ? frontLever : pullUp)
                    .sets(4).reps(8).restSeconds(90).displayOrder(i).build());
        }

        WorkoutPlanDay friLightPull = WorkoutPlanDay.builder().id(205L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.FRIDAY)
                .displayOrder(5).name("Pull (Light)").sessionType(WorkoutSessionType.PULL).restDay(false).build();
        friLightPull.getExercises().add(WorkoutPlanExercise.builder()
                .id(10L).workoutPlanDay(friLightPull).exercise(pullUp).sets(3).reps(8).restSeconds(90).displayOrder(1).build());

        WorkoutPlanDay sunRest = WorkoutPlanDay.builder().id(207L).workoutPlan(plan).dayOfWeek(WorkoutDayOfWeek.SUNDAY)
                .displayOrder(7).name("Rest Day").sessionType(WorkoutSessionType.REST).restDay(true).build();

        plan.getDays().addAll(List.of(tueHeavyPull, friLightPull, sunRest));
        WorkoutPlanContextService.ActiveWorkoutPlanContext ctx = service.buildPlanContext(plan, plan.getDays(), false, null);

        WorkoutPlanContextService.DayRecommendationPlan rec =
                service.evaluateDaysForAddition(ctx, "front lever", List.of(frontLever, frontLeverRaises));

        assertThat(rec.primaryRecommendation().day().dayOfWeek()).isEqualTo(WorkoutDayOfWeek.FRIDAY);
        assertThat(rec.rankedTrainingDays())
                .extracting(e -> e.day().dayOfWeek())
                .doesNotContain(WorkoutDayOfWeek.SUNDAY);
    }
}
