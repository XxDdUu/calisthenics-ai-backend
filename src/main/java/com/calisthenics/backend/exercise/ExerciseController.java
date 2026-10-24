package com.calisthenics.backend.exercise;

import com.calisthenics.backend.exercise.dto.CreateExerciseRequest;
import com.calisthenics.backend.exercise.dto.ExerciseResponse;
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
@RequestMapping("/api/v1/exercises")
@RequiredArgsConstructor
@Tag(name = "Exercises", description = "Calisthenics exercise catalog endpoints")
public class ExerciseController {

    private final ExerciseService exerciseService;

    @GetMapping
    @Operation(summary = "Get list of all calisthenics exercises")
    public ResponseEntity<List<ExerciseResponse>> getAllExercises() {
        return ResponseEntity.ok(exerciseService.getAllExercises());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get exercise detail by ID")
    public ResponseEntity<ExerciseResponse> getExerciseById(@PathVariable Long id) {
        return ResponseEntity.ok(exerciseService.getExerciseById(id));
    }

    @GetMapping("/code/{code}")
    @Operation(summary = "Get exercise detail by unique string code")
    public ResponseEntity<ExerciseResponse> getExerciseByCode(@PathVariable String code) {
        return ResponseEntity.ok(exerciseService.getExerciseByCode(code));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a new exercise entry (Admin only)")
    public ResponseEntity<ExerciseResponse> createExercise(@Valid @RequestBody CreateExerciseRequest request) {
        ExerciseResponse created = exerciseService.createExercise(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
