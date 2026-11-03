package com.calisthenics.backend.profile.controller;

import com.calisthenics.backend.profile.dto.request.ClientProfileRequest;
import com.calisthenics.backend.profile.dto.response.ClientProfileResponse;
import com.calisthenics.backend.profile.dto.response.ClientTrainingOverviewResponse;
import com.calisthenics.backend.profile.service.ClientProfileService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clients")
@RequiredArgsConstructor
@Tag(name = "Clients", description = "Client profile and training overview endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class ClientController {

    private final ClientProfileService clientProfileService;

    @GetMapping("/me")
    @Operation(summary = "Get current user's Client profile and training overview")
    public ResponseEntity<ClientProfileResponse> getMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(clientProfileService.getMyClientProfile(currentUser.getId()));
    }

    @GetMapping("/me/overview")
    @Operation(summary = "Get current user's Client training overview")
    public ResponseEntity<ClientTrainingOverviewResponse> getMyOverview(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(clientProfileService.getClientOverview(currentUser.getId(), currentUser.getId()));
    }

    @PutMapping("/me")
    @Operation(summary = "Create or update current user's Client profile")
    public ResponseEntity<ClientProfileResponse> updateMyProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody ClientProfileRequest request) {
        return ResponseEntity.ok(clientProfileService.updateMyClientProfile(currentUser.getId(), request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Client profile by user ID (Only accessible by the client or their active coach)")
    public ResponseEntity<ClientProfileResponse> getClientProfile(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(clientProfileService.getClientProfile(currentUser.getId(), id));
    }

    @GetMapping("/{id}/overview")
    @Operation(summary = "Get Client training overview (Only accessible by the client or their active coach)")
    public ResponseEntity<ClientTrainingOverviewResponse> getClientOverview(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(clientProfileService.getClientOverview(currentUser.getId(), id));
    }
}
