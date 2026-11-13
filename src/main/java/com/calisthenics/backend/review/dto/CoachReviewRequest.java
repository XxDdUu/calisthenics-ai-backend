package com.calisthenics.backend.review.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CoachReviewRequest(
        @NotNull(message = "Overall rating is required")
        @Min(value = 1, message = "Ratings must be between 1 and 5")
        @Max(value = 5, message = "Ratings must be between 1 and 5")
        Integer overallRating,
        @Min(value = 1, message = "Ratings must be between 1 and 5") @Max(value = 5, message = "Ratings must be between 1 and 5")
        Integer coachingQuality,
        @Min(value = 1, message = "Ratings must be between 1 and 5") @Max(value = 5, message = "Ratings must be between 1 and 5")
        Integer communication,
        @Min(value = 1, message = "Ratings must be between 1 and 5") @Max(value = 5, message = "Ratings must be between 1 and 5")
        Integer planEffectiveness,
        @Min(value = 1, message = "Ratings must be between 1 and 5") @Max(value = 5, message = "Ratings must be between 1 and 5")
        Integer supportResponsiveness,
        @Size(max = 1000, message = "Review must not exceed 1000 characters")
        String comment
) {
}
