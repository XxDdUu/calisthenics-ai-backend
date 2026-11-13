package com.calisthenics.backend.review.dto;

import java.time.Instant;
import java.util.List;

public record AdminCoachReviewResponse(
        Long id,
        Long coachId,
        String coachName,
        Long studentId,
        String studentName,
        int overallRating,
        Integer coachingQuality,
        Integer communication,
        Integer planEffectiveness,
        Integer supportResponsiveness,
        String comment,
        String status,
        String moderationReason,
        Long moderatedByAdminId,
        Instant moderatedAt,
        Instant createdAt,
        Instant updatedAt,
        List<Event> history
) {
    public record Event(
            String type,
            Long actorId,
            int overallRating,
            String comment,
            String note,
            Instant createdAt
    ) {
    }
}
