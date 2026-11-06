package com.calisthenics.backend.workoutplan.dto.request;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class WorkoutPlanDayRequest {

    @NotNull(message = "Day of week is required")
    private WorkoutDayOfWeek dayOfWeek;

    /** Accepts the legacy sessionType property and the clearer workoutType alias. */
    @JsonAlias("workoutType")
    private WorkoutSessionType sessionType;

    /** Free-form workout name; not tied to the optional workout type. */
    @JsonAlias("customWorkoutName")
    @Size(max = 100, message = "Custom workout name must be at most 100 characters")
    private String name;

    @Size(max = 2000, message = "Day description must be at most 2000 characters")
    private String description;

    @JsonAlias("isRestDay")
    private Boolean restDay;

    @Min(value = 1, message = "Display order must be between 1 and 7")
    @Max(value = 7, message = "Display order must be between 1 and 7")
    private Integer displayOrder;

    @Valid
    private List<WorkoutPlanExerciseRequest> exercises;

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    public void setDescription(String description) {
        this.description = trimToNull(description);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
