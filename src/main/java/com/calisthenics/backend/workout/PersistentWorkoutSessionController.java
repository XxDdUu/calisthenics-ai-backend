package com.calisthenics.backend.workout;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.workout.dto.*;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/v1/workout-sessions")
@RequiredArgsConstructor
@Tag(name = "Workout Sessions", description = "Persistent workout sessions, set tracking, and history")
@SecurityRequirement(name = "******")
public class PersistentWorkoutSessionController {

    private final PersistentWorkoutSessionService sessionService;

    @PostMapping
    public ResponseEntity<PersistentWorkoutSessionResponse> startOrResume(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody StartWorkoutSessionRequest request) {
        return ResponseEntity.ok(sessionService.startOrResume(currentUser.getId(), request));
    }

    @GetMapping("/active")
    public ResponseEntity<List<PersistentWorkoutSessionResponse>> getActive(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(sessionService.getActive(currentUser.getId()));
    }

    @GetMapping
    public ResponseEntity<WorkoutSessionPageResponse> getHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be non-negative and size must be 1-100");
        }
        return ResponseEntity.ok(sessionService.getHistory(currentUser.getId(), page, size));
    }

    @GetMapping("/{sessionId}")
    public ResponseEntity<PersistentWorkoutSessionResponse> get(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(sessionService.get(currentUser.getId(), sessionId));
    }

    @PutMapping("/{sessionId}/exercises/{exerciseId}/sets/{setId}")
    public ResponseEntity<PersistentWorkoutSessionResponse> updateSet(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long sessionId,
            @PathVariable Long exerciseId,
            @PathVariable Long setId,
            @Valid @RequestBody UpdateWorkoutSessionSetRequest request) {
        return ResponseEntity.ok(sessionService.updateSet(
                currentUser.getId(), sessionId, exerciseId, setId, request));
    }

    @PutMapping("/{sessionId}/exercises/{exerciseId}")
    public ResponseEntity<PersistentWorkoutSessionResponse> updateExercise(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long sessionId,
            @PathVariable Long exerciseId,
            @Valid @RequestBody UpdateWorkoutSessionExerciseRequest request) {
        return ResponseEntity.ok(sessionService.updateExercise(
                currentUser.getId(), sessionId, exerciseId, request));
    }

    @PostMapping("/{sessionId}/finish")
    public ResponseEntity<PersistentWorkoutSessionResponse> finish(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody FinishWorkoutSessionRequest request) {
        return ResponseEntity.ok(sessionService.finish(currentUser.getId(), sessionId, request));
    }
}
