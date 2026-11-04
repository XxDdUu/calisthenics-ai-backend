package com.calisthenics.backend.analytics.controller;

import com.calisthenics.backend.analytics.dto.BatchSharePrRequest;
import com.calisthenics.backend.analytics.dto.CreatePersonalRecordRequest;
import com.calisthenics.backend.analytics.dto.PersonalRecordResponse;
import com.calisthenics.backend.analytics.dto.SharedPersonalRecordResponse;
import com.calisthenics.backend.analytics.dto.UpdatePersonalRecordRequest;
import com.calisthenics.backend.analytics.model.PersonalRecordType;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;
import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Personal Records", description = "Endpoints for Personal Records tracking, history, and Coach PR sharing")
@SecurityRequirement(name = "bearerAuth")
public class PersonalRecordController {

    private final PersonalRecordService personalRecordService;

    @PostMapping({"/api/analytics/pr", "/api/v1/prs", "/api/v1/analytics/pr"})
    @Operation(summary = "Create a new Personal Record for authenticated user")
    public ResponseEntity<PersonalRecordResponse> createPR(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreatePersonalRecordRequest request) {
        PersonalRecordResponse response = personalRecordService.createPR(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping({"/api/analytics/pr", "/api/v1/prs", "/api/v1/analytics/pr"})
    @Operation(summary = "Get Personal Records for authenticated user with optional filters")
    public ResponseEntity<List<PersonalRecordResponse>> getUserPRs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) Long exerciseId,
            @RequestParam(required = false) PersonalRecordType prType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate) {
        return ResponseEntity.ok(personalRecordService.getUserPRsFiltered(
                currentUser.getId(), exerciseId, prType, startDate, endDate));
    }

    @GetMapping({"/api/analytics/pr/{exerciseId}/history", "/api/v1/prs/{exerciseId}/history"})
    @Operation(summary = "Get historical PR progression for a specific exercise")
    public ResponseEntity<List<PersonalRecordResponse>> getPRHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long exerciseId,
            @RequestParam(required = false) PersonalRecordType prType) {
        return ResponseEntity.ok(personalRecordService.getPRHistory(currentUser.getId(), exerciseId, prType));
    }

    @GetMapping({"/api/analytics/pr/{exerciseId}/latest", "/api/v1/prs/{exerciseId}/latest"})
    @Operation(summary = "Get latest Personal Record for a specific exercise")
    public ResponseEntity<PersonalRecordResponse> getLatestPR(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long exerciseId,
            @RequestParam(required = false) PersonalRecordType prType) {
        return ResponseEntity.ok(personalRecordService.getLatestPR(currentUser.getId(), exerciseId, prType));
    }

    @PutMapping({"/api/analytics/pr/{id}", "/api/v1/prs/{id}", "/api/v1/analytics/pr/{id}"})
    @Operation(summary = "Update an existing Personal Record")
    public ResponseEntity<PersonalRecordResponse> updatePR(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePersonalRecordRequest request) {
        return ResponseEntity.ok(personalRecordService.updatePR(currentUser.getId(), id, request));
    }

    @DeleteMapping({"/api/analytics/pr/{id}", "/api/v1/prs/{id}", "/api/v1/analytics/pr/{id}"})
    @Operation(summary = "Delete an existing Personal Record")
    public ResponseEntity<Void> deletePR(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        personalRecordService.deletePR(currentUser.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/prs/session/{sessionId}")
    @Operation(summary = "Get Personal Records achieved during a specific workout session")
    public ResponseEntity<List<PersonalRecordResponse>> getSessionPRs(
            @PathVariable Long sessionId) {
        return ResponseEntity.ok(personalRecordService.getSessionPRs(sessionId));
    }

    @PostMapping({"/api/v1/clients/me/pr-shares", "/api/v1/prs/batch-share"})
    @Operation(summary = "Share multiple Personal Records with assigned Coach")
    public ResponseEntity<List<SharedPersonalRecordResponse>> batchSharePRs(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody BatchSharePrRequest request) {
        return ResponseEntity.ok(personalRecordService.sharePRsWithCoach(currentUser.getId(), request.getEffectivePrIds()));
    }

    @PostMapping("/api/v1/prs/{prId}/share")
    @Operation(summary = "Share a Personal Record with assigned Coach")
    public ResponseEntity<SharedPersonalRecordResponse> sharePR(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long prId) {
        return ResponseEntity.ok(personalRecordService.sharePRWithCoach(currentUser.getId(), prId));
    }

    @GetMapping({"/api/v1/clients/me/pr-shares", "/api/v1/client/shared-prs"})
    @Operation(summary = "Get list of PRs shared by client, including coach feedback")
    public ResponseEntity<List<SharedPersonalRecordResponse>> getClientSharedPRs(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(personalRecordService.getClientSharedPRs(currentUser.getId()));
    }

    @GetMapping("/api/v1/clients/me/feedback")
    @Operation(summary = "Get all coach feedbacks addressed to the authenticated client")
    public ResponseEntity<List<SharedPersonalRecordResponse>> getClientFeedbacks(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(personalRecordService.getClientFeedbacks(currentUser.getId()));
    }
}
