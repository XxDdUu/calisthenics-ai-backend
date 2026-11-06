package com.calisthenics.backend.workoutplan.service;

import com.calisthenics.backend.workoutplan.dto.request.CreateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.DuplicateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.UpdateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanTemplateResponse;

import java.util.List;

public interface WorkoutPlanService {

    WorkoutPlanResponse create(Long userId, CreateWorkoutPlanRequest request);

    List<WorkoutPlanResponse> listForUser(Long userId);

    WorkoutPlanResponse getActive(Long userId);

    WorkoutPlanResponse get(Long userId, Long planId);

    WorkoutPlanResponse update(Long userId, Long planId, UpdateWorkoutPlanRequest request);

    WorkoutPlanResponse duplicate(Long userId, Long planId, DuplicateWorkoutPlanRequest request);

    WorkoutPlanResponse activate(Long userId, Long planId);

    void delete(Long userId, Long planId);

    List<WorkoutPlanTemplateResponse> getTemplates();
}
