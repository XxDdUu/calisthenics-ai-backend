package com.calisthenics.backend.review.dto;

import java.util.Map;

public record CoachReviewSummaryResponse(
        Long coachId,
        Double averageRating,
        long totalReviews,
        Map<String, Long> distribution,
        Double coachingQualityAvg,
        Double communicationAvg,
        Double planEffectivenessAvg,
        Double supportResponsivenessAvg
) {
}
