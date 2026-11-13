package com.calisthenics.backend.report.dto;

import java.time.Instant;
import java.util.List;

public record AdminUserReportResponse(
        Long id,
        String reason,
        String status,
        PartyInfo reporter,
        PartyInfo reportedUser,
        Instant createdAt,
        Instant updatedAt,
        // detail-only fields (null / empty in list responses)
        String description,
        List<String> evidenceUrls,
        Long handledByAdminId,
        Instant reviewedAt,
        Instant resolvedAt,
        List<PreviousReport> previousReports,
        List<ActionInfo> actions
) {
    public record PartyInfo(Long id, String fullName, String email) {
    }

    public record PreviousReport(Long id, String reason, String status, Instant createdAt) {
    }

    public record ActionInfo(
            Long id,
            String type,
            String fromStatus,
            String toStatus,
            String moderationAction,
            Instant suspendedUntil,
            String note,
            Long adminId,
            String adminName,
            Instant createdAt
    ) {
    }
}
