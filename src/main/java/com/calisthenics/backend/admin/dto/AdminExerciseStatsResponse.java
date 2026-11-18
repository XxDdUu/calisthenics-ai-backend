package com.calisthenics.backend.admin.dto;

public record AdminExerciseStatsResponse(
        long totalCount,
        long publishedCount,
        long draftCount,
        long aiRecommendedCount
) {
}
