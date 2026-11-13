package com.calisthenics.backend.review.dto;

import com.calisthenics.backend.review.entity.CoachReviewStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ModerateCoachReviewRequest(
        @NotNull(message = "Status is required") CoachReviewStatus status,
        @Size(max = 500, message = "Reason must not exceed 500 characters") String reason
) {
}
