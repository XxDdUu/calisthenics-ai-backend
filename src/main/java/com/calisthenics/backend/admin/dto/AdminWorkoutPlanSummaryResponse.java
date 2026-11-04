package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;

import java.time.ZonedDateTime;

public record AdminWorkoutPlanSummaryResponse(
        Long id,
        Long userId,
        String userEmail,
        String userFullName,
        String name,
        WorkoutSplitType splitType,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {
}
