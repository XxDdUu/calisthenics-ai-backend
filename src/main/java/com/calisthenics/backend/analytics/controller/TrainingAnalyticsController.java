package com.calisthenics.backend.analytics.controller;

import com.calisthenics.backend.analytics.dto.*;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.analytics.service.TrainingAnalyticsService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Training Analytics & Progress", description = "Endpoints for progressive overload, exercise history, and training insights")
@SecurityRequirement(name = "bearerAuth")
public class TrainingAnalyticsController {

    private final TrainingAnalyticsService analyticsService;
    private final PersonalRecordService personalRecordService;

    @GetMapping("/api/v1/analytics/overview")
    @Operation(summary = "Get user training consistency, streaks, and overall stats")
    public ResponseEntity<TrainingOverviewResponse> getOverview(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        return ResponseEntity.ok(analyticsService.getTrainingOverview(currentUser.getId(), timezone));
    }

    @GetMapping("/api/v1/analytics/weekly")
    @Operation(summary = "Get current vs previous week training comparison")
    public ResponseEntity<WeeklyAnalyticsResponse> getWeeklyAnalytics(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        return ResponseEntity.ok(analyticsService.getWeeklyAnalytics(currentUser.getId(), timezone));
    }

    @GetMapping("/api/v1/analytics/monthly")
    @Operation(summary = "Get current vs previous month training analytics")
    public ResponseEntity<MonthlyAnalyticsResponse> getMonthlyAnalytics(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        return ResponseEntity.ok(analyticsService.getMonthlyAnalytics(currentUser.getId(), timezone));
    }

    @GetMapping("/api/v1/analytics/insights")
    @Operation(summary = "Get deterministic, data-backed training insights")
    public ResponseEntity<TrainingInsightsResponse> getInsights(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        return ResponseEntity.ok(analyticsService.getTrainingInsights(currentUser.getId(), timezone));
    }

    @GetMapping("/api/v1/exercises/{exerciseId}/progress")
    @Operation(summary = "Get exercise details, progressive overload comparison, chart data, and history")
    public ResponseEntity<ExerciseProgressResponse> getExerciseProgress(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long exerciseId,
            @RequestParam(required = false) String filter,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        return ResponseEntity.ok(analyticsService.getExerciseProgress(currentUser.getId(), exerciseId, filter, timezone));
    }

    @GetMapping("/api/v1/exercises/{exerciseId}/history")
    @Operation(summary = "Get completed session history for an exercise")
    public ResponseEntity<List<ExerciseHistorySessionDto>> getExerciseHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long exerciseId,
            @RequestParam(required = false) String filter,
            @RequestHeader(value = "X-Timezone", required = false) String timezone) {
        ExerciseProgressResponse progress = analyticsService.getExerciseProgress(currentUser.getId(), exerciseId, filter, timezone);
        return ResponseEntity.ok(progress.getSessions());
    }

    @GetMapping("/api/v1/exercises/{exerciseId}/records")
    @Operation(summary = "Get all Personal Records achieved for an exercise")
    public ResponseEntity<List<PersonalRecordResponse>> getExerciseRecords(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long exerciseId) {
        return ResponseEntity.ok(personalRecordService.getExercisePRs(currentUser.getId(), exerciseId));
    }
}

