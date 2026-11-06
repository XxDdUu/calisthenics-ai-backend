package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;

import java.util.List;
public record CreateAdminExerciseRequest(
        @NotBlank(message = "Exercise name is required")
        @Size(max = 100, message = "Exercise name must not exceed 100 characters")
        String name,

        @Size(max = 50, message = "Exercise code must not exceed 50 characters")
        String code,

        @NotNull(message = "Category is required")
        ExerciseCategory category,

        @NotNull(message = "Difficulty is required")
        DifficultyLevel difficulty,

        String description,

        String targetMuscles,

        String instructions,

        @Pattern(regexp = "^(https?://.*)?$", message = "Image URL must be a valid HTTP or HTTPS URL")
        String imageUrl,

        @Pattern(regexp = "^(https?://.*)?$", message = "Video URL must be a valid HTTP or HTTPS URL")
        String videoUrl,

        String equipment,

        Boolean active,

        @Size(max = 100, message = "An exercise cannot have more than 100 media items")
        List<@Valid ExerciseMediaItemRequest> mediaItems
) {
}
