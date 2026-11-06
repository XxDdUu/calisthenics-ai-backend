package com.calisthenics.backend.exercise.dto;

import com.calisthenics.backend.exercise.ExerciseMediaType;

public record ExerciseMediaResponse(
        Long id,
        ExerciseMediaType type,
        String url,
        String thumbnailUrl,
        int displayOrder
) {
}
