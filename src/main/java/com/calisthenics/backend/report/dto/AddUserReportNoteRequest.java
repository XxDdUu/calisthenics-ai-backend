package com.calisthenics.backend.report.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddUserReportNoteRequest(
        @NotBlank(message = "Note is required")
        @Size(max = 2000, message = "Note must not exceed 2000 characters") String note
) {
}
