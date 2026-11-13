package com.calisthenics.backend.report.dto;

import com.calisthenics.backend.report.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateUserReportRequest(
        @NotNull(message = "Reason is required") ReportReason reason,
        @Size(max = 2000, message = "Description must not exceed 2000 characters") String description,
        @Size(max = 5, message = "At most 5 evidence files are allowed") List<String> evidenceUrls
) {
}
