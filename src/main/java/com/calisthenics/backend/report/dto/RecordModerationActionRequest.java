package com.calisthenics.backend.report.dto;

import com.calisthenics.backend.report.entity.ModerationAction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public record RecordModerationActionRequest(
        @NotNull(message = "Action is required") ModerationAction action,
        Instant suspendedUntil,
        @Size(max = 2000, message = "Note must not exceed 2000 characters") String note
) {
}
