package com.calisthenics.backend.exercisetree.controller;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercisetree.dto.ExerciseTreeCategoriesResponse;
import com.calisthenics.backend.exercisetree.dto.ExerciseTreeExerciseDetailResponse;
import com.calisthenics.backend.exercisetree.dto.ExerciseTreeResponse;
import com.calisthenics.backend.exercisetree.dto.ExerciseTreeUserProgressResponse;
import com.calisthenics.backend.exercisetree.service.ExerciseTreeService;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping({"/api/v1/exercise-trees", "/api/exercise-trees"})
@RequiredArgsConstructor
@Tag(name = "Exercise Trees", description = "Interactive Calisthenics and Gym exercise progression trees")
public class ExerciseTreeController {

    private final ExerciseTreeService exerciseTreeService;

    @GetMapping
    @Operation(summary = "Get an exercise progression tree by training category (CALISTHENICS or GYM)")
    public ResponseEntity<ExerciseTreeResponse> getTree(
            @RequestParam(defaultValue = "CALISTHENICS") String category,
            @RequestParam(required = false) String subcategory,
            @RequestParam(required = false) DifficultyLevel difficulty,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        ExerciseTreeCategoryType type;
        try {
            type = ExerciseTreeCategoryType.fromString(category);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category: " + category);
        }
        Long userId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(exerciseTreeService.getTree(type, subcategory, difficulty, search, userId));
    }

    @GetMapping("/categories")
    @Operation(summary = "List Calisthenics and Gym tree categories with exercise and difficulty counts")
    public ResponseEntity<ExerciseTreeCategoriesResponse> getCategories() {
        return ResponseEntity.ok(exerciseTreeService.getCategories());
    }

    @GetMapping("/user-progress")
    @Operation(summary = "Get authenticated user progression states across all exercise tree nodes")
    public ResponseEntity<ExerciseTreeUserProgressResponse> getUserProgress(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        if (currentUser == null || currentUser.getId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return ResponseEntity.ok(exerciseTreeService.getUserProgress(currentUser.getId()));
    }

    @GetMapping("/exercises/{exerciseId}")
    @Operation(summary = "Get rich exercise progression detail including previous, next, prerequisite, and related exercises")
    public ResponseEntity<ExerciseTreeExerciseDetailResponse> getExerciseDetail(
            @PathVariable Long exerciseId,
            @RequestParam(required = false) String category,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        ExerciseTreeCategoryType preferred = null;
        if (category != null && !category.isBlank()) {
            try {
                preferred = ExerciseTreeCategoryType.fromString(category);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid category: " + category);
            }
        }
        Long userId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(exerciseTreeService.getExerciseDetail(exerciseId, preferred, userId));
    }

    @GetMapping("/{category}/{subcategory}")
    @Operation(summary = "Get exercise progression tree for a specific training category and subcategory")
    public ResponseEntity<ExerciseTreeResponse> getSubcategoryTree(
            @PathVariable String category,
            @PathVariable String subcategory,
            @RequestParam(required = false) DifficultyLevel difficulty,
            @RequestParam(required = false) String search,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        Long userId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(exerciseTreeService.getSubcategoryTree(category, subcategory, difficulty, search, userId));
    }
}
