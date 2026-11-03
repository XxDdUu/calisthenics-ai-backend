package com.calisthenics.backend.workoutplan.service;

import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanExerciseResponse;

import java.util.List;

public interface WorkoutPlanExerciseService {

    List<WorkoutPlanExerciseResponse> list(Long userId, Long planId, Long dayId);

    WorkoutPlanExerciseResponse add(Long userId, Long planId, Long dayId, WorkoutPlanExerciseRequest request);

    WorkoutPlanExerciseResponse update(
            Long userId, Long planId, Long dayId, Long assignmentId, WorkoutPlanExerciseRequest request);

    List<WorkoutPlanExerciseResponse> updateAll(
            Long userId, Long planId, Long dayId, List<WorkoutPlanExerciseRequest> exercises);

    void remove(Long userId, Long planId, Long dayId, Long assignmentId);
}
