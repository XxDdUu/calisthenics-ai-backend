package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;

import java.time.ZonedDateTime;

public record AdminExerciseResponse(
        Long id,
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
        boolean active,
        ZonedDateTime createdAt,
        long planReferencesCount,
        long sessionReferencesCount,
        boolean canDelete
) {
}
