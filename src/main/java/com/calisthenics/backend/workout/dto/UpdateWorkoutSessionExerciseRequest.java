package com.calisthenics.backend.workout.dto;

import com.calisthenics.backend.workout.WorkoutSessionExerciseStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record UpdateWorkoutSessionExerciseRequest(
        @NotNull WorkoutSessionExerciseStatus status,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
