package com.calisthenics.backend.report.dto;

public record UserReportSummaryResponse(long total, long pending, long underReview, long resolved, long dismissed) {
}
