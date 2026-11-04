package com.calisthenics.backend.admin.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateExerciseStatusRequest(
        @NotNull(message = "Active status is required")
        Boolean active
) {
}
