package com.calisthenics.backend.review.controller;

import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.review.dto.*;
import com.calisthenics.backend.review.service.CoachReviewService;
import com.calisthenics.backend.security.UserPrincipal;
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
@RequestMapping("/api/v1/coaches/{coachId}/reviews")
@RequiredArgsConstructor
@Tag(name = "Coach Reviews", description = "Student ratings and reviews of coaches")
@SecurityRequirement(name = "Bearer Authentication")
public class CoachReviewController {

    private final CoachReviewService reviewService;

    @GetMapping("/summary")
    @Operation(summary = "Average rating, rating distribution and criteria averages")
    public ResponseEntity<CoachReviewSummaryResponse> summary(@PathVariable Long coachId) {
        return ResponseEntity.ok(reviewService.getSummary(coachId));
    }

    @GetMapping
    @Operation(summary = "List visible reviews for a coach")
    public ResponseEntity<AdminPageResponse<CoachReviewResponse>> list(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long coachId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(reviewService.listVisible(currentUser.getId(), coachId, page, size));
    }

    @GetMapping("/mine")
    @Operation(summary = "Current user's review of this coach and whether they may review")
    public ResponseEntity<MyCoachReviewResponse> mine(
            @AuthenticationPrincipal UserPrincipal currentUser, @PathVariable Long coachId) {
        return ResponseEntity.ok(reviewService.getMine(currentUser.getId(), coachId));
    }

    @PostMapping
    @Operation(summary = "Submit a review (one per coach)")
    public ResponseEntity<CoachReviewResponse> create(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long coachId,
            @Valid @RequestBody CoachReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(currentUser.getId(), coachId, request));
    }

    @PutMapping("/mine")
    @Operation(summary = "Edit the current user's review")
    public ResponseEntity<CoachReviewResponse> edit(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long coachId,
            @Valid @RequestBody CoachReviewRequest request) {
        return ResponseEntity.ok(reviewService.edit(currentUser.getId(), coachId, request));
    }
}
