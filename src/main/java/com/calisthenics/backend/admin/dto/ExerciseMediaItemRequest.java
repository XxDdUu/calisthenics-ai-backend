package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.exercise.ExerciseMediaType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ExerciseMediaItemRequest(
        @NotNull ExerciseMediaType type,
        @NotBlank @Size(max = 2048)
        @Pattern(regexp = "^https?://.*$", message = "Media URL must be a valid HTTP or HTTPS URL")
        String url,
        @Size(max = 2048)
        @Pattern(regexp = "^(https?://.*)?$", message = "Thumbnail URL must be a valid HTTP or HTTPS URL")
        String thumbnailUrl,
        @PositiveOrZero Integer displayOrder
) {
}
