package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateAdminExerciseRequest(
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
        List<@Valid ExerciseMediaItemRequest> mediaItems,

        Boolean replaceMediaItems,

        ExerciseStatus status,

        ExerciseOrigin origin,

        ExerciseTrainingType trainingType,

        String secondaryMuscles,

        String exerciseType,

        Integer recommendedSets,

        String recommendedReps,

        Integer recommendedRestSeconds,

        String commonMistakes,

        String safetyConsiderations,

        String progressionNotes,

        String aliases
) {
    public UpdateAdminExerciseRequest(
            String name,
            String code,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            String description,
            String targetMuscles,
            String instructions,
            String imageUrl,
            String videoUrl,
            String equipment,
            Boolean active,
            List<ExerciseMediaItemRequest> mediaItems
    ) {
        this(
                name, code, category, difficulty, description, targetMuscles, instructions,
                imageUrl, videoUrl, equipment, active, mediaItems, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    public UpdateAdminExerciseRequest(
            String name,
            String code,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            String description,
            String targetMuscles,
            String instructions,
            String imageUrl,
            String videoUrl,
            String equipment,
            Boolean active,
            List<ExerciseMediaItemRequest> mediaItems,
            Boolean replaceMediaItems
    ) {
        this(
                name, code, category, difficulty, description, targetMuscles, instructions,
                imageUrl, videoUrl, equipment, active, mediaItems, replaceMediaItems, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
    }

    public UpdateAdminExerciseRequest(
            String name,
            String code,
            ExerciseCategory category,
            DifficultyLevel difficulty,
            String description,
            String targetMuscles,
            String instructions,
            String imageUrl,
            String videoUrl,
            String equipment,
            Boolean active,
            List<ExerciseMediaItemRequest> mediaItems,
            Boolean replaceMediaItems,
            ExerciseStatus status,
            ExerciseOrigin origin
    ) {
        this(
                name, code, category, difficulty, description, targetMuscles, instructions,
                imageUrl, videoUrl, equipment, active, mediaItems, replaceMediaItems, status, origin,
                null, null, null, null, null, null, null, null, null, null
        );
    }
}
