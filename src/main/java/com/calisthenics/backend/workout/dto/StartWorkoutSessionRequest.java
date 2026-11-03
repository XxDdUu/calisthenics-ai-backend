package com.calisthenics.backend.workout.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record StartWorkoutSessionRequest(
        @NotNull @Positive Long planId,
        @NotNull @Positive Long dayId,
        @NotNull LocalDate workoutDate,
        @Positive Long assignmentId
) {
}
