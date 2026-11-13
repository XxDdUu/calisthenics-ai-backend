package com.calisthenics.backend.review.dto;

import java.time.Instant;

/** Student-safe view of a review: no student id, email or full name. */
public record CoachReviewResponse(
        Long id,
        int overallRating,
        Integer coachingQuality,
        Integer communication,
        Integer planEffectiveness,
        Integer supportResponsiveness,
        String comment,
        String reviewerName,
        String reviewerAvatarUrl,
        boolean mine,
        boolean edited,
        Instant createdAt,
        Instant updatedAt
) {
}
