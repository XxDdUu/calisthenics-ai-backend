package com.calisthenics.backend.workoutplan.dto.request;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class WorkoutPlanDayRequest {

    @NotNull(message = "Day of week is required")
    private WorkoutDayOfWeek dayOfWeek;

    private WorkoutSessionType sessionType;

    @Size(max = 100, message = "Day name must be at most 100 characters")
    private String name;

    @Valid
    private List<WorkoutPlanExerciseRequest> exercises;

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? null : name.trim();
    }

    public void setExercises(List<WorkoutPlanExerciseRequest> exercises) {
        this.exercises = exercises;
    }
}
