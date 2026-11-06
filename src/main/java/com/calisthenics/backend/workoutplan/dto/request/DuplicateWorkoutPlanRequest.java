package com.calisthenics.backend.workoutplan.dto.request;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DuplicateWorkoutPlanRequest {

    @Size(max = 100, message = "Plan name must be at most 100 characters")
    private String name;

    public void setName(String name) {
        this.name = name == null || name.isBlank() ? null : name.trim();
    }
}
