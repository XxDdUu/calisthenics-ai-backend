package com.calisthenics.backend.workout.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateWorkoutSessionSetRequest(
        @Min(0) @Max(10000) Integer reps,
        @Min(0) @Max(86400) Integer holdDurationSeconds,
        @DecimalMin("0.00") @DecimalMax("2000.00") BigDecimal weightKg,
        @DecimalMin("0.00") @DecimalMax("2000.00") BigDecimal assistanceWeightKg,
        @NotNull Boolean completed,
        @Size(max = 2000) String notes,
        @NotNull @PositiveOrZero Long expectedVersion
) {
}
