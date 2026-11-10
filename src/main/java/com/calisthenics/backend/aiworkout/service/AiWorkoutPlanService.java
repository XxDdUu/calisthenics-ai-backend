package com.calisthenics.backend.aiworkout.service;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.ModifyAiWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;

public interface AiWorkoutPlanService {

    AiWorkoutPlanPreviewResponse generatePlan(Long userId, GenerateAiWorkoutPlanRequest request);

    default AiWorkoutPlanPreviewResponse generatePlan(Long userId, GenerateAiWorkoutPlanRequest request, String timezone) {
        return generatePlan(userId, request);
    }

    AiWorkoutPlanPreviewResponse modifyPlan(Long userId, ModifyAiWorkoutPlanRequest request);

    default AiWorkoutPlanPreviewResponse modifyPlan(Long userId, ModifyAiWorkoutPlanRequest request, String timezone) {
        return modifyPlan(userId, request);
    }

    WorkoutPlanResponse commitPlan(Long userId, CommitAiWorkoutPlanRequest request);

    default WorkoutPlanResponse commitPlan(Long userId, CommitAiWorkoutPlanRequest request, String timezone) {
        return commitPlan(userId, request);
    }
}
