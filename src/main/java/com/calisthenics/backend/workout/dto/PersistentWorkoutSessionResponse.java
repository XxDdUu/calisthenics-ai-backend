package com.calisthenics.backend.workout.dto;

import com.calisthenics.backend.workout.WorkoutSessionClassification;
import com.calisthenics.backend.workout.WorkoutSessionStatus;
import com.calisthenics.backend.workout.WorkoutSessionSource;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

public record PersistentWorkoutSessionResponse(
        Long id,
        Long workoutPlanId,
        Long workoutPlanDayId,
        Long assignmentId,
        WorkoutSessionSource source,
        String title,
        LocalDate workoutDate,
        WorkoutSessionStatus status,
        WorkoutSessionClassification classification,
        ZonedDateTime startedAt,
        ZonedDateTime lastSavedAt,
        ZonedDateTime completedAt,
        Long durationSeconds,
        Integer completedExercises,
        Integer skippedExercises,
        Integer unfinishedExercises,
        Integer completedSets,
        Integer totalReps,
        Integer totalHoldDurationSeconds,
        List<WorkoutSessionExerciseResponse> exercises,
        Long gettingStartedEnrollmentId,
        Integer gettingStartedDayNumber
) {
}
