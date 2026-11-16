package com.calisthenics.backend.user;

import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.dto.UpdateGenderRequest;
import com.calisthenics.backend.user.dto.UpdateMembershipRequest;
import com.calisthenics.backend.user.dto.UpdateOnboardingStatusRequest;
import com.calisthenics.backend.user.dto.UpdateProfileRequest;
import com.calisthenics.backend.user.dto.UserProfileResponse;
import com.calisthenics.backend.videoanalysis.VideoAnalysisQuotaService;
import com.calisthenics.backend.videoanalysis.dto.QuotaEntitlementResponseDto;
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
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and account management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;
    private final VideoAnalysisQuotaService videoAnalysisQuotaService;

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<UserProfileResponse> getCurrentUser(@AuthenticationPrincipal UserPrincipal currentUser) {
        UserProfileResponse profile = userService.getUserProfile(currentUser.getId());
        return ResponseEntity.ok(profile);
    }

    @PutMapping("/me")
    @Operation(summary = "Update authenticated user profile information")
    public ResponseEntity<UserProfileResponse> updateCurrentUser(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestBody UpdateProfileRequest updateRequest) {
        UserProfileResponse updatedProfile = userService.updateUserProfile(currentUser.getId(), updateRequest);
        return ResponseEntity.ok(updatedProfile);
    }

    @PatchMapping("/me/gender")
    @Operation(summary = "Update authenticated user's gender")
    public ResponseEntity<UserProfileResponse> updateCurrentUserGender(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateGenderRequest updateGenderRequest) {
        UserProfileResponse updatedProfile = userService.updateUserGender(
                currentUser.getId(),
                updateGenderRequest.getGender()
        );
        return ResponseEntity.ok(updatedProfile);
    }

    @PatchMapping("/me/onboarding-status")
    @Operation(summary = "Update authenticated user's onboarding status")
    public ResponseEntity<UserProfileResponse> updateCurrentUserOnboardingStatus(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateOnboardingStatusRequest updateRequest) {
        UserProfileResponse updatedProfile = userService.updateUserOnboardingStatus(
                currentUser.getId(),
                updateRequest.getStatus()
        );
        return ResponseEntity.ok(updatedProfile);
    }

    @GetMapping("/me/entitlements")
    @Operation(summary = "Get authenticated user's membership entitlements and daily video analysis quota")
    public ResponseEntity<QuotaEntitlementResponseDto> getCurrentUserEntitlements(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        return ResponseEntity.ok(videoAnalysisQuotaService.getQuotaStatus(currentUser.getId()));
    }

    @PutMapping("/me/membership")
    @Operation(summary = "Update authenticated user's membership tier (FREE or PRO)")
    public ResponseEntity<QuotaEntitlementResponseDto> updateCurrentUserMembership(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateMembershipRequest request) {
        if (currentUser == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User must be authenticated.");
        }
        return ResponseEntity.ok(
                videoAnalysisQuotaService.updateMembershipTier(currentUser.getId(), request.getMembershipTier())
        );
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get list of all registered users (Admin only)")
    public ResponseEntity<List<UserProfileResponse>> getAllUsers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }
}
