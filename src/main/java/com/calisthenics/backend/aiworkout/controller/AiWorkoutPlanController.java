package com.calisthenics.backend.aiworkout.controller;

import com.calisthenics.backend.aiworkout.dto.AiWorkoutPlanPreviewResponse;
import com.calisthenics.backend.aiworkout.dto.CommitAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.GenerateAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.dto.ModifyAiWorkoutPlanRequest;
import com.calisthenics.backend.aiworkout.service.AiWorkoutPlanService;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/workout-plans")
@RequiredArgsConstructor
@Tag(name = "AI Workout Planner", description = "Natural-language workout planning powered by Google Gemini API")
@SecurityRequirement(name = "Bearer Authentication")
public class AiWorkoutPlanController {

    private final AiWorkoutPlanService aiWorkoutPlanService;

    @PostMapping("/generate")
    @Operation(summary = "Generate a structured workout plan preview from natural language")
    public ResponseEntity<AiWorkoutPlanPreviewResponse> generate(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody GenerateAiWorkoutPlanRequest request
    ) {
        return ResponseEntity.ok(aiWorkoutPlanService.generatePlan(currentUser.getId(), request));
    }

    @PostMapping("/modify")
    @Operation(summary = "Iteratively modify an existing plan preview using natural language")
    public ResponseEntity<AiWorkoutPlanPreviewResponse> modify(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ModifyAiWorkoutPlanRequest request
    ) {
        return ResponseEntity.ok(aiWorkoutPlanService.modifyPlan(currentUser.getId(), request));
    }

    @PostMapping("/commit")
    @Operation(summary = "Commit and persist the validated AI workout plan and schedule into calendar")
    public ResponseEntity<WorkoutPlanResponse> commit(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CommitAiWorkoutPlanRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(aiWorkoutPlanService.commitPlan(currentUser.getId(), request));
    }
}
