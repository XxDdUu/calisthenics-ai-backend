package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;

import java.time.ZonedDateTime;
import java.util.List;

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
        ExerciseStatus status,
        ExerciseOrigin origin,
        ZonedDateTime createdAt,
        long planReferencesCount,
        long sessionReferencesCount,
        boolean canDelete,
        List<ExerciseMediaResponse> mediaItems,
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
    public AdminExerciseResponse(
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
            boolean canDelete,
            List<ExerciseMediaResponse> mediaItems
    ) {
        this(
                id,
                name,
                code,
                category,
                difficulty,
                description,
                targetMuscles,
                instructions,
                imageUrl,
                videoUrl,
                equipment,
                active,
                active ? ExerciseStatus.PUBLISHED : ExerciseStatus.DRAFT,
                ExerciseOrigin.MANUAL,
                createdAt,
                planReferencesCount,
                sessionReferencesCount,
                canDelete,
                mediaItems,
                ExerciseTrainingType.CALISTHENICS,
                null, null, null, null, null, null, null, null, null
        );
    }

    public AdminExerciseResponse(
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
            ExerciseStatus status,
            ExerciseOrigin origin,
            ZonedDateTime createdAt,
            long planReferencesCount,
            long sessionReferencesCount,
            boolean canDelete,
            List<ExerciseMediaResponse> mediaItems
    ) {
        this(
                id,
                name,
                code,
                category,
                difficulty,
                description,
                targetMuscles,
                instructions,
                imageUrl,
                videoUrl,
                equipment,
                active,
                status,
                origin,
                createdAt,
                planReferencesCount,
                sessionReferencesCount,
                canDelete,
                mediaItems,
                ExerciseTrainingType.CALISTHENICS,
                null, null, null, null, null, null, null, null, null
        );
    }
}
