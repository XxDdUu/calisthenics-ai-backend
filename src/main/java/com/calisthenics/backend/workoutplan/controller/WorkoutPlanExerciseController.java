package com.calisthenics.backend.workoutplan.controller;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutDayExercisesBatchRequest;
import com.calisthenics.backend.workoutplan.dto.request.WorkoutPlanExerciseRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanExerciseResponse;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanExerciseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workout-plans/{planId}/days/{dayId}/exercises")
@RequiredArgsConstructor
public class WorkoutPlanExerciseController {

    private final WorkoutPlanExerciseService workoutPlanExerciseService;

    @GetMapping
    public ResponseEntity<List<WorkoutPlanExerciseResponse>> list(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long planId,
            @PathVariable Long dayId) {
        return ResponseEntity.ok(workoutPlanExerciseService.list(currentUser.getId(), planId, dayId));
    }

    @PostMapping
    public ResponseEntity<WorkoutPlanExerciseResponse> add(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long planId,
            @PathVariable Long dayId,
            @Valid @RequestBody WorkoutPlanExerciseRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workoutPlanExerciseService.add(currentUser.getId(), planId, dayId, request));
    }

    @PutMapping
    public ResponseEntity<List<WorkoutPlanExerciseResponse>> updateAll(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long planId,
            @PathVariable Long dayId,
            @Valid @RequestBody WorkoutDayExercisesBatchRequest request) {
        return ResponseEntity.ok(workoutPlanExerciseService.updateAll(
                currentUser.getId(), planId, dayId, request.getExercises()));
    }

    @PutMapping("/{assignmentId}")
    public ResponseEntity<WorkoutPlanExerciseResponse> update(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long planId,
            @PathVariable Long dayId,
            @PathVariable Long assignmentId,
            @Valid @RequestBody WorkoutPlanExerciseRequest request) {
        return ResponseEntity.ok(workoutPlanExerciseService.update(
                currentUser.getId(), planId, dayId, assignmentId, request));
    }

    @DeleteMapping("/{assignmentId}")
    public ResponseEntity<Void> remove(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long planId,
            @PathVariable Long dayId,
            @PathVariable Long assignmentId) {
        workoutPlanExerciseService.remove(currentUser.getId(), planId, dayId, assignmentId);
        return ResponseEntity.noContent().build();
    }
}
