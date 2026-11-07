package com.calisthenics.backend.coachclient.controller;

import com.calisthenics.backend.coachclient.dto.request.CreateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.request.UpdateWorkoutPlanAssignmentRequest;
import com.calisthenics.backend.coachclient.dto.response.WorkoutPlanAssignmentResponse;
import com.calisthenics.backend.coachclient.service.WorkoutPlanAssignmentService;
import com.calisthenics.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class WorkoutPlanAssignmentController {

    private final WorkoutPlanAssignmentService assignmentService;

    @PostMapping("/coach-client-relationships/{relationshipId}/assignments")
    public ResponseEntity<WorkoutPlanAssignmentResponse> assign(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long relationshipId,
            @Valid @RequestBody CreateWorkoutPlanAssignmentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assignmentService.assign(currentUser.getId(), relationshipId, request));
    }

    @GetMapping("/coach-client-relationships/{relationshipId}/assignments")
    public ResponseEntity<List<WorkoutPlanAssignmentResponse>> listForRelationship(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long relationshipId) {
        return ResponseEntity.ok(assignmentService.listForRelationship(currentUser.getId(), relationshipId));
    }

    @PutMapping("/coach-client-relationships/{relationshipId}/assignments/{assignmentId}")
    public ResponseEntity<WorkoutPlanAssignmentResponse> update(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long relationshipId,
            @PathVariable Long assignmentId,
            @Valid @RequestBody UpdateWorkoutPlanAssignmentRequest request) {
        return ResponseEntity.ok(assignmentService.update(
                currentUser.getId(), relationshipId, assignmentId, request));
    }

    @GetMapping("/clients/me/workout-plan-assignments")
    public ResponseEntity<List<WorkoutPlanAssignmentResponse>> listForCurrentClient(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(assignmentService.listForClient(currentUser.getId()));
    }

    @GetMapping("/coach/me/workout-plan-assignments")
    public ResponseEntity<List<WorkoutPlanAssignmentResponse>> listForCurrentCoach(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(assignmentService.listForCoach(currentUser.getId()));
    }
}
