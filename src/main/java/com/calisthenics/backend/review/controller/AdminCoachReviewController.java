package com.calisthenics.backend.review.controller;

import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.review.dto.AdminCoachReviewResponse;
import com.calisthenics.backend.review.dto.ModerateCoachReviewRequest;
import com.calisthenics.backend.review.entity.CoachReviewStatus;
import com.calisthenics.backend.review.service.CoachReviewService;
import com.calisthenics.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reviews")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminCoachReviewController {

    private final CoachReviewService reviewService;

    @GetMapping
    public ResponseEntity<AdminPageResponse<AdminCoachReviewResponse>> list(
            @RequestParam(required = false) CoachReviewStatus status,
            @RequestParam(required = false) Long coachId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reviewService.adminList(status, coachId, page, size));
    }

    @GetMapping("/{reviewId}")
    public ResponseEntity<AdminCoachReviewResponse> get(@PathVariable Long reviewId) {
        return ResponseEntity.ok(reviewService.adminGet(reviewId));
    }

    @PutMapping("/{reviewId}/moderation")
    public ResponseEntity<AdminCoachReviewResponse> moderate(
            @PathVariable Long reviewId,
            @Valid @RequestBody ModerateCoachReviewRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        return ResponseEntity.ok(reviewService.moderate(reviewId, admin.getId(), request));
    }
}
