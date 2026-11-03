package com.calisthenics.backend.workout.dto;

import com.calisthenics.backend.workout.WorkoutSessionExerciseStatus;

import java.util.List;

public record WorkoutSessionExerciseResponse(
        Long id,
        Long version,
        Long exerciseId,
        String exerciseName,
        String instructions,
        String notes,
        Integer orderIndex,
        Integer targetSets,
        Integer targetReps,
        Integer targetHoldDurationSeconds,
        Integer restSeconds,
        WorkoutSessionExerciseStatus status,
        List<WorkoutSessionSetResponse> sets
) {
}
