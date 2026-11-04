package com.calisthenics.backend.bodymetrics.controller;

import com.calisthenics.backend.bodymetrics.dto.CoachClientBodyMetricsResponse;
import com.calisthenics.backend.bodymetrics.service.BodyMetricsService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.ZonedDateTime;

@RestController
@RequestMapping("/api/v1/coach/clients")
@RequiredArgsConstructor
@Tag(name = "Coach Client Body Metrics", description = "Coach access to assigned client body measurements and progress")
@SecurityRequirement(name = "bearerAuth")
public class CoachBodyMetricsController {

    private final BodyMetricsService bodyMetricsService;

    @GetMapping("/{clientId}/body-metrics")
    @Operation(summary = "Get assigned client's current height, weight and body measurement history")
    public ResponseEntity<CoachClientBodyMetricsResponse> getCoachClientBodyMetrics(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long clientId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size,
            @RequestParam(defaultValue = "asc") String sort
    ) {
        return ResponseEntity.ok(bodyMetricsService.getCoachClientBodyMetrics(
                currentUser.getId(),
                clientId,
                startDate,
                endDate,
                page,
                size,
                sort
        ));
    }
}

