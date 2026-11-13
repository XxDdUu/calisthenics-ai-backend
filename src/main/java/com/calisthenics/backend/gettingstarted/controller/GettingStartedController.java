package com.calisthenics.backend.gettingstarted.controller;

import com.calisthenics.backend.gettingstarted.dto.GettingStartedEnrollmentResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import com.calisthenics.backend.gettingstarted.dto.UpdateDismissalStatusRequest;
import com.calisthenics.backend.gettingstarted.service.GettingStartedService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/getting-started-program")
@RequiredArgsConstructor
@Tag(name = "Getting Started Program", description = "Athlete endpoints for Getting Started 3-Day program")
@SecurityRequirement(name = "Bearer Authentication")
public class GettingStartedController {

    private final GettingStartedService gettingStartedService;

    @GetMapping({"", "/", "/published"})
    @Operation(summary = "Get published Getting Started program")
    public ResponseEntity<GettingStartedProgramResponse> getPublishedProgram(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = (currentUser != null) ? currentUser.getId() : null;
        GettingStartedProgramResponse response = gettingStartedService.getPublishedProgram(userId);
        if (response == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/dismissal-status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update athlete onboarding dismissal status for the Getting Started program")
    public ResponseEntity<GettingStartedProgramResponse> updateDismissalStatus(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateDismissalStatusRequest request) {
        return ResponseEntity.ok(gettingStartedService.updateDismissalStatus(currentUser.getId(), request.getStatus()));
    }

    @PostMapping("/enroll")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Enroll in the published Getting Started program")
    public ResponseEntity<GettingStartedEnrollmentResponse> enroll(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gettingStartedService.enrollAthlete(currentUser.getId()));
    }

    @PostMapping("/complete-day/{dayNumber}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark a day completed in the Getting Started program")
    public ResponseEntity<GettingStartedEnrollmentResponse> completeDay(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Integer dayNumber) {
        return ResponseEntity.ok(gettingStartedService.completeDay(currentUser.getId(), dayNumber));
    }
}
