package com.calisthenics.backend.report.controller;

import com.calisthenics.backend.admin.dto.AdminPageResponse;
import com.calisthenics.backend.report.dto.*;
import com.calisthenics.backend.report.entity.UserReportStatus;
import com.calisthenics.backend.report.service.UserReportService;
import com.calisthenics.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/user-reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserReportController {

    private final UserReportService reportService;

    @GetMapping("/summary")
    public ResponseEntity<UserReportSummaryResponse> summary() {
        return ResponseEntity.ok(reportService.summary());
    }

    @GetMapping
    public ResponseEntity<AdminPageResponse<AdminUserReportResponse>> list(
            @RequestParam(required = false) UserReportStatus status,
            @RequestParam(required = false) Long reportedUserId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(reportService.list(status, reportedUserId, page, size));
    }

    @GetMapping("/{reportId}")
    public ResponseEntity<AdminUserReportResponse> get(@PathVariable Long reportId) {
        return ResponseEntity.ok(reportService.get(reportId));
    }

    @PutMapping("/{reportId}/status")
    public ResponseEntity<AdminUserReportResponse> changeStatus(
            @PathVariable Long reportId,
            @Valid @RequestBody UpdateUserReportStatusRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        return ResponseEntity.ok(reportService.changeStatus(reportId, admin.getId(), request));
    }

    @PostMapping("/{reportId}/notes")
    public ResponseEntity<AdminUserReportResponse> addNote(
            @PathVariable Long reportId,
            @Valid @RequestBody AddUserReportNoteRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        return ResponseEntity.ok(reportService.addNote(reportId, admin.getId(), request));
    }

    @PostMapping("/{reportId}/actions")
    public ResponseEntity<AdminUserReportResponse> recordAction(
            @PathVariable Long reportId,
            @Valid @RequestBody RecordModerationActionRequest request,
            @AuthenticationPrincipal UserPrincipal admin) {
        return ResponseEntity.ok(reportService.recordAction(reportId, admin.getId(), request));
    }
}
