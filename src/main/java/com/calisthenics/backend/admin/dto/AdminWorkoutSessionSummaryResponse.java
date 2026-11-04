package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.workout.WorkoutSessionStatus;

import java.time.LocalDate;
import java.time.ZonedDateTime;

public record AdminWorkoutSessionSummaryResponse(
        Long id,
        Long userId,
        String userEmail,
        String userFullName,
        String title,
        LocalDate workoutDate,
        WorkoutSessionStatus status,
        Long workoutPlanId,
        String workoutPlanName,
        ZonedDateTime createdAt,
        ZonedDateTime startedAt,
        ZonedDateTime completedAt
) {
}
