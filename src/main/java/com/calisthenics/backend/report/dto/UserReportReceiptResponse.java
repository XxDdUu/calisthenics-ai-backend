package com.calisthenics.backend.report.dto;

import java.time.Instant;

/** What the reporter gets back; deliberately contains nothing about the reported account. */
public record UserReportReceiptResponse(Long id, String status, Instant createdAt) {
}
