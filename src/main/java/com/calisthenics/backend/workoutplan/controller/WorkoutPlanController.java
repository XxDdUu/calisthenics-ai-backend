package com.calisthenics.backend.workoutplan.controller;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.workoutplan.dto.request.CreateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.request.UpdateWorkoutPlanRequest;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanTemplateResponse;
import com.calisthenics.backend.workoutplan.service.WorkoutPlanService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workout-plans")
@RequiredArgsConstructor
@Tag(name = "Workout Plans", description = "Personal weekly workout plan management")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkoutPlanController {

    private final WorkoutPlanService workoutPlanService;

    @PostMapping
    public ResponseEntity<WorkoutPlanResponse> create(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateWorkoutPlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(workoutPlanService.create(currentUser.getId(), request));
    }

    @GetMapping
    public ResponseEntity<List<WorkoutPlanResponse>> list(@AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(workoutPlanService.listForUser(currentUser.getId()));
    }

    @GetMapping("/templates")
    public ResponseEntity<List<WorkoutPlanTemplateResponse>> templates() {
        return ResponseEntity.ok(workoutPlanService.getTemplates());
    }

    @GetMapping("/{id}")
    public ResponseEntity<WorkoutPlanResponse> get(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(workoutPlanService.get(currentUser.getId(), id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<WorkoutPlanResponse> update(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateWorkoutPlanRequest request) {
        return ResponseEntity.ok(workoutPlanService.update(currentUser.getId(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        workoutPlanService.delete(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
