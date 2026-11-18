package com.calisthenics.backend.admin;

import com.calisthenics.backend.admin.dto.AdminExerciseDeleteResponse;
import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.AdminExerciseStatsResponse;
import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.admin.dto.BulkUpdateExerciseStatusRequest;
import com.calisthenics.backend.admin.dto.CreateAdminExerciseRequest;
import com.calisthenics.backend.admin.dto.UpdateAdminExerciseRequest;
import com.calisthenics.backend.admin.dto.UpdateExerciseStatusRequest;
import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseStatus;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/exercises")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Exercises", description = "Exercise library management endpoints for administrators")
public class AdminExerciseController {

    private final AdminExerciseService adminExerciseService;

    @GetMapping
    @Operation(summary = "List exercises with pagination, search, and filters (Admin only)")
    public ResponseEntity<AdminPageResponse<AdminExerciseResponse>> getExercises(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) ExerciseCategory category,
            @RequestParam(required = false) DifficultyLevel difficulty,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) ExerciseStatus status,
            @RequestParam(required = false) ExerciseOrigin origin,
            @RequestParam(required = false) ExerciseTrainingType trainingType,
            @RequestParam(defaultValue = "name") String sortBy,
            @RequestParam(defaultValue = "ASC") String sortDirection
    ) {
        if (status == null && origin == null && trainingType == null) {
            return ResponseEntity.ok(adminExerciseService.getExercises(
                    page, size, search, category, difficulty, active, sortBy, sortDirection));
        }
        return ResponseEntity.ok(adminExerciseService.getExercises(
                page, size, search, category, difficulty, active, status, origin, trainingType, sortBy, sortDirection));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get exercise counts by publication status and AI origin (Admin only)")
    public ResponseEntity<AdminExerciseStatsResponse> getExerciseStats() {
        return ResponseEntity.ok(adminExerciseService.getExerciseStats());
    }

    @PatchMapping("/bulk-status")
    @Operation(summary = "Bulk publish or return exercises to draft (Admin only)")
    public ResponseEntity<List<AdminExerciseResponse>> bulkUpdateStatus(
            @Valid @RequestBody BulkUpdateExerciseStatusRequest request
    ) {
        return ResponseEntity.ok(adminExerciseService.bulkUpdateStatus(
                request.exerciseIds(), request.resolveActive()));
    }

    @GetMapping("/{exerciseId}")
    @Operation(summary = "Get exercise details with usage statistics (Admin only)")
    public ResponseEntity<AdminExerciseResponse> getExercise(@PathVariable Long exerciseId) {
        return ResponseEntity.ok(adminExerciseService.getExercise(exerciseId));
    }

    @PostMapping
    @Operation(summary = "Create a new exercise (Admin only)")
    public ResponseEntity<AdminExerciseResponse> createExercise(
            @Valid @RequestBody CreateAdminExerciseRequest request
    ) {
        AdminExerciseResponse response = adminExerciseService.createExercise(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{exerciseId}")
    @Operation(summary = "Update an existing exercise (Admin only)")
    public ResponseEntity<AdminExerciseResponse> updateExercise(
            @PathVariable Long exerciseId,
            @Valid @RequestBody UpdateAdminExerciseRequest request
    ) {
        return ResponseEntity.ok(adminExerciseService.updateExercise(exerciseId, request));
    }

    @PatchMapping("/{exerciseId}/status")
    @Operation(summary = "Activate/publish or deactivate/draft an exercise (Admin only)")
    public ResponseEntity<AdminExerciseResponse> updateExerciseStatus(
            @PathVariable Long exerciseId,
            @Valid @RequestBody UpdateExerciseStatusRequest request
    ) {
        return ResponseEntity.ok(adminExerciseService.updateStatus(exerciseId, request.resolveActive()));
    }

    @DeleteMapping("/{exerciseId}")
    @Operation(summary = "Safely delete or soft-deactivate an exercise (Admin only)")
    public ResponseEntity<AdminExerciseDeleteResponse> deleteExercise(
            @PathVariable Long exerciseId,
            @RequestParam(defaultValue = "false") boolean softDelete
    ) {
        return ResponseEntity.ok(adminExerciseService.deleteExercise(exerciseId, softDelete));
    }
}
