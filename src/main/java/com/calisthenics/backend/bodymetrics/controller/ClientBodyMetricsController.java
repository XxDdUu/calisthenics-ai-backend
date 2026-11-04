package com.calisthenics.backend.bodymetrics.controller;

import com.calisthenics.backend.bodymetrics.dto.*;
import com.calisthenics.backend.bodymetrics.service.BodyMetricsService;
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

@RestController
@RequestMapping("/api/v1/clients/me/body-metrics")
@RequiredArgsConstructor
@Tag(name = "Client Body Metrics", description = "Endpoints for clients to manage their body measurements and height")
@SecurityRequirement(name = "bearerAuth")
public class ClientBodyMetricsController {

    private final BodyMetricsService bodyMetricsService;

    @GetMapping("/current")
    @Operation(summary = "Get authenticated client's current height, latest weight and optional body measurements")
    public ResponseEntity<CurrentBodyMetricsResponse> getCurrentMetrics(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        return ResponseEntity.ok(bodyMetricsService.getCurrentMetrics(currentUser.getId()));
    }

    @PostMapping
    @Operation(summary = "Record a new body measurement entry")
    public ResponseEntity<BodyMeasurementResponse> recordMeasurement(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody RecordBodyMeasurementRequest request
    ) {
        BodyMeasurementResponse response = bodyMetricsService.recordMeasurement(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/height")
    @Operation(summary = "Update client's current profile height")
    public ResponseEntity<CurrentBodyMetricsResponse> updateHeight(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody UpdateHeightRequest request
    ) {
        return ResponseEntity.ok(bodyMetricsService.updateHeight(currentUser.getId(), request));
    }

    @GetMapping("/history")
    @Operation(summary = "Get historical body measurements with pagination and optional date filters")
    public ResponseEntity<BodyMetricsHistoryResponse> getHistory(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) ZonedDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "desc") String sort
    ) {
        return ResponseEntity.ok(bodyMetricsService.getMeasurementHistory(
                currentUser.getId(),
                startDate,
                endDate,
                page,
                size,
                sort
        ));
    }
}

