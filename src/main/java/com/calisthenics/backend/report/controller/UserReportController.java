package com.calisthenics.backend.report.controller;

import com.calisthenics.backend.report.dto.CreateUserReportRequest;
import com.calisthenics.backend.report.dto.UserReportReceiptResponse;
import com.calisthenics.backend.report.service.UserReportService;
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
@RequestMapping("/api/v1/users/{userId}/reports")
@RequiredArgsConstructor
@Tag(name = "User Reports", description = "Report another user to the moderation team")
@SecurityRequirement(name = "Bearer Authentication")
public class UserReportController {

    private final UserReportService reportService;

    @PostMapping
    @Operation(summary = "Report a user")
    public ResponseEntity<UserReportReceiptResponse> report(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long userId,
            @Valid @RequestBody CreateUserReportRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reportService.submit(currentUser.getId(), userId, request));
    }
}
