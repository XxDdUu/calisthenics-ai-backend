package com.calisthenics.backend.exercise.muscle;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Muscle Taxonomy", description = "Canonical anatomical muscle groups and SVG target metadata")
public class MuscleController {

    private final MuscleTaxonomyService muscleTaxonomyService;

    @GetMapping({"/api/v1/muscles", "/api/v1/exercises/muscles"})
    @Operation(summary = "Get the canonical 22-muscle anatomical taxonomy")
    public ResponseEntity<List<MuscleGroupDto>> getAllMuscles() {
        return ResponseEntity.ok(muscleTaxonomyService.getAllMuscles());
    }
}
