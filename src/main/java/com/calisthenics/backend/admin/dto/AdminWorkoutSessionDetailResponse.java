package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.workout.dto.PersistentWorkoutSessionResponse;

public record AdminWorkoutSessionDetailResponse(
        Long userId,
        String userEmail,
        String userFullName,
        PersistentWorkoutSessionResponse workoutSession
) {
}
