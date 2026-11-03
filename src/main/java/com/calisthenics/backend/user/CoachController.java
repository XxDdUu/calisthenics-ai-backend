package com.calisthenics.backend.user;

import com.calisthenics.backend.profile.dto.request.CoachProfileRequest;
import com.calisthenics.backend.profile.service.CoachProfileService;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.dto.CoachProfileResponse;
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
@RequestMapping("/api/v1/coaches")
@RequiredArgsConstructor
@Tag(name = "Coaches", description = "Coach profile and public directory endpoints")
public class CoachController {

    private final CoachProfileService coachProfileService;

    @GetMapping("/me")
    @Operation(summary = "Get current user's Coach profile")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<CoachProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(coachProfileService.getMyCoachProfile(currentUser.getId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Create or update current user's Coach profile")
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<CoachProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CoachProfileRequest request) {
        return ResponseEntity.ok(coachProfileService.updateMyCoachProfile(currentUser.getId(), request));
    }

    @GetMapping
    @Operation(summary = "List public coaches in directory")
    public ResponseEntity<List<CoachProfileResponse>> list(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(coachProfileService.listPublicCoaches(currentUserId));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get public Coach profile by coach user ID")
    public ResponseEntity<CoachProfileResponse> get(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        Long currentUserId = currentUser != null ? currentUser.getId() : null;
        return ResponseEntity.ok(coachProfileService.getPublicCoachProfile(currentUserId, id));
    }
}
