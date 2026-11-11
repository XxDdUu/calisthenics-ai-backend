package com.calisthenics.backend.coachclient.service;

import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.request.UpdateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.request.UpdateWorkoutPlanAssignmentStatusRequest;
import com.calisthenics.backend.coachclient.dto.response.WorkoutPlanAssignmentResponse;
import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;

import java.util.List;

public interface WorkoutPlanAssignmentService {

    WorkoutPlanAssignmentResponse assign(
            Long coachId, Long relationshipId, CreateWorkoutPlanAssignmentRequest request);

    List<WorkoutPlanAssignmentResponse> listForRelationship(Long coachId, Long relationshipId);

    List<WorkoutPlanAssignmentResponse> listForRelationship(
            Long coachId, Long relationshipId, boolean includeCancelled, WorkoutPlanAssignmentStatus status);

    List<WorkoutPlanAssignmentResponse> listForClient(Long clientId);

    List<WorkoutPlanAssignmentResponse> listForClient(
            Long clientId, boolean includeCancelled, WorkoutPlanAssignmentStatus status);

    List<WorkoutPlanAssignmentResponse> listForCoach(Long coachId);

    List<WorkoutPlanAssignmentResponse> listForCoach(
            Long coachId, boolean includeCancelled, WorkoutPlanAssignmentStatus status);

    WorkoutPlanAssignmentResponse update(
            Long coachId, Long relationshipId, Long assignmentId, UpdateWorkoutPlanAssignmentRequest request);

    WorkoutPlanAssignmentResponse updateStatus(
            Long coachId, Long assignmentId, UpdateWorkoutPlanAssignmentStatusRequest request);
}
