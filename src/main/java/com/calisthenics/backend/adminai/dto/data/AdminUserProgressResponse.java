package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserProgressResponse {
    private AdminTimeRangeMetadata timeRange;
    private Long userId;
    private String fullName;
    private double overallCompletionRate;
    private long currentPeriodWorkouts;
    private long previousPeriodWorkouts;
    private Double workoutVolumeDeltaPercent;
    private String consistencyTrend;
    private List<PeriodPointDto> weeklySessionCounts;
    private List<ExercisePerformanceItem> exercisePerformance;
    private List<StrengthProgressionItem> strengthProgression;
    private List<BodyMeasurementPointDto> bodyMetricTrends;
    private List<String> missingDataNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PeriodPointDto {
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String label;
        private long completedCount;
        private long totalCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExercisePerformanceItem {
        private String exerciseName;
        private long attempts;
        private long completedCount;
        private double completionRate;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StrengthProgressionItem {
        private String exerciseName;
        private String recordType;
        private BigDecimal latestValue;
        private String unit;
        private BigDecimal bestImprovementPercent;
        private long prCount;
        private ZonedDateTime lastAchievedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BodyMeasurementPointDto {
        private BigDecimal weightKg;
        private BigDecimal bodyFatPercentage;
        private ZonedDateTime measuredAt;
    }
}
