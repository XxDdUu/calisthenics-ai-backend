package com.calisthenics.backend.coachclient.service;

import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.request.UpdateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.response.WorkoutPlanAssignmentResponse;

import java.util.List;

public interface WorkoutPlanAssignmentService {

    WorkoutPlanAssignmentResponse assign(
            Long coachId, Long relationshipId, CreateWorkoutPlanAssignmentRequest request);

    List<WorkoutPlanAssignmentResponse> listForRelationship(Long coachId, Long relationshipId);

    List<WorkoutPlanAssignmentResponse> listForClient(Long clientId);

    List<WorkoutPlanAssignmentResponse> listForCoach(Long coachId);

    WorkoutPlanAssignmentResponse update(
            Long coachId, Long relationshipId, Long assignmentId, UpdateWorkoutPlanAssignmentRequest request);
}
