package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.exercise.ExerciseStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record BulkUpdateExerciseStatusRequest(
        @NotEmpty(message = "Exercise IDs are required")
        List<Long> exerciseIds,
        Boolean active,
        ExerciseStatus status
) {
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
