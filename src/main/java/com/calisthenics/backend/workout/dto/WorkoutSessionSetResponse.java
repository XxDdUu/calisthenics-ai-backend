package com.calisthenics.backend.workout.dto;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record WorkoutSessionSetResponse(
        Long id,
        Long version,
        Integer setNumber,
        Integer reps,
        Integer holdDurationSeconds,
        BigDecimal weightKg,
        BigDecimal assistanceWeightKg,
        boolean completed,
        ZonedDateTime completedAt,
        String notes
) {
}
