package com.calisthenics.backend.review.dto;

public record MyCoachReviewResponse(boolean eligible, String reason, CoachReviewResponse review) {
}
