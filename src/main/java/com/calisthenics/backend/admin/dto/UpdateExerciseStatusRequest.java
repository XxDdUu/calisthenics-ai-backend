package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.exercise.ExerciseStatus;
import jakarta.validation.constraints.AssertTrue;

public record UpdateExerciseStatusRequest(
        Boolean active,
        ExerciseStatus status
) {
    public UpdateExerciseStatusRequest(Boolean active) {
        this(active, null);
    }

    @AssertTrue(message = "Active status or exercise status is required")
    public boolean isValidStatusUpdate() {
        return active != null || status != null;
    }

    public boolean resolveActive() {
        if (status != null) {
            return status == ExerciseStatus.PUBLISHED;
        }
        return Boolean.TRUE.equals(active);
    }
}
