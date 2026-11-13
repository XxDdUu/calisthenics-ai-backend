package com.calisthenics.backend.report.dto;

import com.calisthenics.backend.report.entity.UserReportStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserReportStatusRequest(
        @NotNull(message = "Status is required") UserReportStatus status,
        @Size(max = 2000, message = "Note must not exceed 2000 characters") String note
) {
}
