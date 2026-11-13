package com.calisthenics.backend.gettingstarted.controller;

import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
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
@RequestMapping("/api/v1/admin/getting-started-program")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Getting Started Program", description = "Admin endpoints to manage the 3-day Getting Started program")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminGettingStartedController {

    private final GettingStartedService gettingStartedService;

    @GetMapping
    @Operation(summary = "Get current Getting Started program for admin")
    public ResponseEntity<GettingStartedProgramResponse> getProgram() {
        GettingStartedProgramResponse response = gettingStartedService.getAdminProgram();
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Create or update Getting Started program")
    public ResponseEntity<GettingStartedProgramResponse> createOrUpdateProgram(
            @AuthenticationPrincipal UserPrincipal admin,
            @Valid @RequestBody AdminGettingStartedProgramRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(gettingStartedService.createOrUpdateProgram(admin.getId(), request));
    }

    @PostMapping("/publish")
    @Operation(summary = "Publish Getting Started program")
    public ResponseEntity<GettingStartedProgramResponse> publishProgram(
            @AuthenticationPrincipal UserPrincipal admin) {
        return ResponseEntity.ok(gettingStartedService.publishProgram(admin.getId()));
    }

    @PostMapping("/unpublish")
    @Operation(summary = "Unpublish Getting Started program")
    public ResponseEntity<GettingStartedProgramResponse> unpublishProgram(
            @AuthenticationPrincipal UserPrincipal admin) {
        return ResponseEntity.ok(gettingStartedService.unpublishProgram(admin.getId()));
    }

    @DeleteMapping
    @Operation(summary = "Delete Getting Started program")
    public ResponseEntity<Void> deleteProgram(
            @AuthenticationPrincipal UserPrincipal admin) {
        gettingStartedService.deleteProgram(admin.getId());
        return ResponseEntity.noContent().build();
    }
}
