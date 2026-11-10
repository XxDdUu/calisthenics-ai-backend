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
public class CompleteAnalysisSessionRequest {
    private Long exerciseId;
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
    private List<ExerciseFormMetricDto> exerciseMetrics;
    private List<DetectedFormErrorDto> detectedErrors;
    private String originalVideoUrl;
    private String annotatedVideoUrl;
    private List<RepHistoryDto> reps;
}
