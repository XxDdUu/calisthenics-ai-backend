package com.calisthenics.backend.workoutplan.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutDayExercisesBatchRequest {

    @NotNull(message = "Exercises list is required")
    @Valid
    @Builder.Default
    private List<WorkoutPlanExerciseRequest> exercises = new ArrayList<>();
}

