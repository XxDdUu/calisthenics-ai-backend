package com.calisthenics.backend.workoutplan.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class WorkoutPlanExerciseRequest {

    /**
     * Existing workout-plan exercise assignment ID. Optional for new exercises; include it
     * on plan updates to preserve the existing assignment while changing its configuration.
     */
    @Positive(message = "Exercise assignment ID must be positive when provided")
    private Long id;

    @NotNull(message = "Exercise ID is required")
    @Positive(message = "Exercise ID must be positive")
    private Long exerciseId;

    @Positive(message = "Sets must be positive when provided")
    private Integer sets;

    @Positive(message = "Reps must be positive when provided")
    private Integer reps;

    @Positive(message = "Duration must be positive when provided")
    private Integer durationSeconds;

    @PositiveOrZero(message = "Rest time must be zero or positive when provided")
    private Integer restSeconds;

    @Positive(message = "Display order must be positive when provided")
    private Integer displayOrder;

    @Size(max = 2000, message = "Notes must be at most 2000 characters")
    private String notes;
}
