package com.calisthenics.backend.workout;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.workout.dto.CreateWorkoutSessionRequest;
import com.calisthenics.backend.workout.dto.WorkoutSessionResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/workouts")
@RequiredArgsConstructor
@Tag(name = "Workouts", description = "Workout session and set/rep logging endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class WorkoutController {

    private final WorkoutService workoutService;

    @PostMapping
    @Operation(summary = "Log a new workout session with exercise sets/reps")
    public ResponseEntity<WorkoutSessionResponse> createSession(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateWorkoutSessionRequest request) {
        WorkoutSessionResponse response = workoutService.createSession(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Get user's logged workout session history")
    public ResponseEntity<List<WorkoutSessionResponse>> getUserSessions(@AuthenticationPrincipal UserPrincipal currentUser) {
        List<WorkoutSessionResponse> sessions = workoutService.getUserSessions(currentUser.getId());
        return ResponseEntity.ok(sessions);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get detailed workout session by ID")
    public ResponseEntity<WorkoutSessionResponse> getSessionById(@PathVariable Long id) {
        WorkoutSessionResponse response = workoutService.getSessionById(id);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete workout session log")
    public ResponseEntity<Void> deleteSession(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        workoutService.deleteSession(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }
}
