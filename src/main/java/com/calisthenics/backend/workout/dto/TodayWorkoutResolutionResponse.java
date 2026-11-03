package com.calisthenics.backend.workout.dto;

import com.calisthenics.backend.workout.WorkoutSessionSource;

import java.time.LocalDate;
import java.util.List;

public record TodayWorkoutResolutionResponse(
        LocalDate workoutDate,
        boolean activeCoach,
        WorkoutSessionSource source,
        boolean selectionRequired,
        Long selectedPlanId,
        List<TodayWorkoutCandidateResponse> candidates
) {
}
