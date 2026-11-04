package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;

public record AdminWorkoutPlanDetailResponse(
        Long userId,
        String userEmail,
        String userFullName,
        WorkoutPlanResponse workoutPlan
) {
}
