package com.calisthenics.backend.coachclient.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class CreateWorkoutPlanAssignmentRequest {

    @NotNull(message = "Workout plan ID is required")
    @Positive(message = "Workout plan ID must be positive")
    private Long workoutPlanId;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 4000, message = "Notes must be at most 4000 characters")
    private String notes;
}
