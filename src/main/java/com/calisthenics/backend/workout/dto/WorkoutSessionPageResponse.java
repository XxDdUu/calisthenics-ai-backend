package com.calisthenics.backend.workout.dto;

import java.util.List;

public record WorkoutSessionPageResponse(
        List<PersistentWorkoutSessionResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
