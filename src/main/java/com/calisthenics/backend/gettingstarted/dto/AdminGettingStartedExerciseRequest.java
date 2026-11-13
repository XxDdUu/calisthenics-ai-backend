package com.calisthenics.backend.gettingstarted.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminGettingStartedExerciseRequest {

    private Long exerciseId;

    @NotBlank(message = "Exercise name is required")
    private String exerciseName;

    @NotNull(message = "Sets count is required")
    @Min(value = 1, message = "Sets must be at least 1")
    @Builder.Default
    private Integer sets = 3;

    @Min(value = 1, message = "Reps must be at least 1")
    private Integer reps;

    @Min(value = 1, message = "Duration must be at least 1 second")
    private Integer durationSeconds;

    @NotNull(message = "Rest seconds is required")
    @Min(value = 0, message = "Rest seconds cannot be negative")
    @Builder.Default
    private Integer restSeconds = 60;

    private String instructions;

    @Builder.Default
    private Integer displayOrder = 0;
}
