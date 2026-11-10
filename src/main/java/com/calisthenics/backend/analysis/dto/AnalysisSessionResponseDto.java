package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisSessionResponseDto {
    private String id;
    private Long userId;
    private String exerciseType;
    private Long exerciseId;
    private Instant startedAt;
    private Instant completedAt;
    private BigDecimal durationSeconds;
    private Integer totalReps;
    private BigDecimal overallScore;
    private BigDecimal averageFormScore;
    private BigDecimal bestFormScore;
    private BigDecimal lowestFormScore;
    private BigDecimal analysisConfidence;
    private BigDecimal peakHoldDurationSeconds;
    private Integer correctReps;
    private Integer incorrectReps;
    private BigDecimal averageKneeAngle;
    private BigDecimal deepestKneeAngle;
    private BigDecimal averageJointAngle;
    private BigDecimal deepestJointAngle;
    private String analysisStatus;
    private String cameraView;
    private Instant createdAt;
    private List<ExerciseFormMetricDto> exerciseMetrics;
    private List<DetectedFormErrorDto> detectedErrors;
    private String aiSummary;
    private AiPerformanceSummaryDto aiRecommendations;
    private String aiSummaryStatus;
    private String originalVideoUrl;
    private String annotatedVideoUrl;
    private List<RepHistoryDto> reps;
}
