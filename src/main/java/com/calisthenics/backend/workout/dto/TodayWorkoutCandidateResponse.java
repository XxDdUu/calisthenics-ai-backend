package com.calisthenics.backend.workout.dto;

import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanDayResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;

public record TodayWorkoutCandidateResponse(
        WorkoutPlanResponse workoutPlan,
        WorkoutPlanDayResponse today,
        Long assignmentId,
        String coachName
) {
}
