package com.calisthenics.backend.analytics.controller;

import com.calisthenics.backend.analytics.dto.*;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.analytics.service.TrainingAnalyticsService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Coach Analytics & Client PRs", description = "Coach view for client training progress, charts, and PR feedback")
@SecurityRequirement(name = "bearerAuth")
public class CoachAnalyticsController {

    private final TrainingAnalyticsService analyticsService;
    private final PersonalRecordService personalRecordService;

    @GetMapping("/api/v1/coach/clients/{clientId}/progress")
    @Operation(summary = "Get authorized client's overall training overview and stats")
    public ResponseEntity<TrainingOverviewResponse> getClientProgress(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long clientId) {
        return ResponseEntity.ok(analyticsService.getCoachClientOverview(currentUser.getId(), clientId));
    }

    @GetMapping("/api/v1/coach/clients/{clientId}/exercises/{exerciseId}/progress")
    @Operation(summary = "Get authorized client's exercise history and chart data")
    public ResponseEntity<ExerciseProgressResponse> getClientExerciseProgress(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long clientId,
            @PathVariable Long exerciseId,
            @RequestParam(required = false) String filter) {
        return ResponseEntity.ok(analyticsService.getCoachClientExerciseProgress(currentUser.getId(), clientId, exerciseId, filter));
    }

    @GetMapping("/api/v1/coach/clients/{clientId}/analytics/muscle-map")
    @Operation(summary = "Get authorized client's weekly muscle volume distribution and heatmap analytics")
    public ResponseEntity<WeeklyMuscleAnalyticsResponse> getClientWeeklyMuscleAnalytics(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long clientId,
            @RequestParam(defaultValue = "0") int weekOffset,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        return ResponseEntity.ok(analyticsService.getCoachClientWeeklyMuscleAnalytics(
                currentUser.getId(), clientId, weekOffset, timezone));
    }

    @GetMapping("/api/v1/coach/clients/{clientId}/prs")
    @Operation(summary = "Get shared PRs from an authorized client")
    public ResponseEntity<List<SharedPersonalRecordResponse>> getClientPRs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long clientId) {
        return ResponseEntity.ok(personalRecordService.getCoachClientPRs(currentUser.getId(), clientId));
    }

    @GetMapping({"/api/v1/coach/clients/{clientId}/personal-records", "/api/analytics/coach/clients/{clientId}/pr"})
    @Operation(summary = "Get all Personal Records of an authorized client with optional filters")
    public ResponseEntity<List<PersonalRecordResponse>> getClientPersonalRecords(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long clientId,
            @RequestParam(required = false) Long exerciseId,
            @RequestParam(required = false) PersonalRecordType prType) {
        return ResponseEntity.ok(personalRecordService.getCoachClientPrRecords(
                currentUser.getId(), clientId, exerciseId, prType));
    }

    @GetMapping({"/api/v1/coach/clients/{clientId}/pr/{exerciseId}/history", "/api/analytics/coach/clients/{clientId}/pr/{exerciseId}/history"})
    @Operation(summary = "Get historical PR progression for an authorized client's exercise")
    public ResponseEntity<List<PersonalRecordResponse>> getClientPrHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long clientId,
            @PathVariable Long exerciseId,
            @RequestParam(required = false) PersonalRecordType prType) {
        // Enforce coach-client authorization before returning client's PR history
        personalRecordService.getCoachClientPrRecords(currentUser.getId(), clientId, exerciseId, prType);
        return ResponseEntity.ok(personalRecordService.getPRHistory(clientId, exerciseId, prType));
    }

    @GetMapping({"/api/v1/coach/me/pr-shares", "/api/v1/coach/shared-prs"})
    @Operation(summary = "Get all shared PRs from all connected clients for the coach, with optional unread filter")
    public ResponseEntity<List<SharedPersonalRecordResponse>> getAllSharedPRs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) Boolean unread) {
        return ResponseEntity.ok(personalRecordService.getCoachAllSharedPRs(currentUser.getId(), unread));
    }

    @PatchMapping({"/api/v1/coach/me/pr-shares/{shareId}/read", "/api/v1/coach/pr-shares/{shareId}/read"})
    @Operation(summary = "Mark a client's shared PR as read by the coach")
    public ResponseEntity<SharedPersonalRecordResponse> markShareAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long shareId) {
        return ResponseEntity.ok(personalRecordService.markShareAsRead(currentUser.getId(), shareId));
    }

    @PostMapping({"/api/v1/coach/me/pr-shares/{shareId}/feedback", "/api/v1/coach/pr-shares/{shareId}/feedback"})
    @Operation(summary = "Leave feedback on a client's shared Personal Record")
    public ResponseEntity<SharedPersonalRecordResponse> addFeedback(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long shareId,
            @Valid @RequestBody CoachFeedbackRequest request) {
        return ResponseEntity.ok(personalRecordService.addCoachFeedback(
                currentUser.getId(),
                shareId,
                request.getEffectiveFeedback(),
                request.getRating()
        ));
    }
}
