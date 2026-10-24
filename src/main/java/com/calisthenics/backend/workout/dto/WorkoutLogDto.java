package com.calisthenics.backend.workout.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class WorkoutLogDto {

    private Long id;

    @NotNull(message = "Exercise ID is required")
    private Long exerciseId;

    private String exerciseName;
    private String exerciseCode;

    @Builder.Default
    private Integer sets = 1;

    @Builder.Default
    private Integer reps = 0;

    @Builder.Default
    private BigDecimal weightKg = BigDecimal.ZERO;

    @Builder.Default
    private Integer holdTimeSeconds = 0;

    private String notes;
}
