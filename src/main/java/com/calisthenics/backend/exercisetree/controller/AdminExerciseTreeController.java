package com.calisthenics.backend.exercisetree.controller;

import com.calisthenics.backend.exercisetree.dto.*;
import com.calisthenics.backend.exercisetree.service.ExerciseTreeSeederService;
import com.calisthenics.backend.exercisetree.service.ExerciseTreeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/v1/admin/exercise-trees", "/api/admin/exercise-trees"})
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Exercise Trees", description = "Audit and maintain exercise progression tree classifications and relationships")
public class AdminExerciseTreeController {

    private final ExerciseTreeService exerciseTreeService;

    @GetMapping("/audit")
    @Operation(summary = "Generate full catalog audit report for exercise trees (Admin only)")
    public ResponseEntity<AdminExerciseTreeAuditResponse> getAuditReport() {
        return ResponseEntity.ok(exerciseTreeService.getAuditReport());
    }

    @PostMapping("/sync")
    @Operation(summary = "Idempotently sync unclassified exercises into progression trees without modifying Exercise records (Admin only)")
    public ResponseEntity<ExerciseTreeSeederService.SeedSummary> syncCatalog() {
        return ResponseEntity.ok(exerciseTreeService.syncCatalog());
    }

    @PutMapping("/nodes/{nodeId}")
    @Operation(summary = "Update progression node difficulty, subcategory, mastery criteria, or review notes (Admin only)")
    public ResponseEntity<ExerciseTreeNodeDto> updateNode(
            @PathVariable Long nodeId,
            @Valid @RequestBody UpdateExerciseTreeNodeRequest request
    ) {
        return ResponseEntity.ok(exerciseTreeService.updateNode(nodeId, request));
    }

    @PostMapping("/edges")
    @Operation(summary = "Create a progression relationship or prerequisite between two existing exercises (Admin only)")
    public ResponseEntity<ExerciseProgressionEdgeDto> createEdge(
            @Valid @RequestBody CreateExerciseProgressionEdgeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exerciseTreeService.createEdge(request));
    }

    @DeleteMapping("/edges/{edgeId}")
    @Operation(summary = "Delete a progression relationship edge (Admin only)")
    public ResponseEntity<Void> deleteEdge(@PathVariable Long edgeId) {
        exerciseTreeService.deleteEdge(edgeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/missing-nodes")
    @Operation(summary = "Record a missing progression node gap without creating a fake exercise (Admin only)")
    public ResponseEntity<ExerciseTreeMissingNodeDto> createMissingNode(
            @Valid @RequestBody CreateMissingProgressionNodeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED).body(exerciseTreeService.createMissingNode(request));
    }

    @DeleteMapping("/missing-nodes/{id}")
    @Operation(summary = "Delete a missing progression node report (Admin only)")
    public ResponseEntity<Void> deleteMissingNode(@PathVariable Long id) {
        exerciseTreeService.deleteMissingNode(id);
        return ResponseEntity.noContent().build();
    }
}
