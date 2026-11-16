package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoAnalysisResponseDto {
    private Long id;
    private Long userId;
    private Long exerciseId;
    private String exerciseCode;
    private String exerciseName;
    private String schemaVersion;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED, NO_PERSON_DETECTED, INSUFFICIENT_POSE_QUALITY, INVALID_INPUT
    private BigDecimal overallScore;
    private BigDecimal formAccuracy;
    private BigDecimal stabilityScore;
    private BigDecimal alignmentScore;
    private Integer repCount;
    private Integer validReps;
    private Integer incompleteReps;
    private Long durationMs;
    private Integer width;
    private Integer height;
    private BigDecimal fps;
    private String originalVideoUrl;
    private String annotatedVideoUrl;
    private String keypointsUrl;
    private Map<String, Object> metrics;
    private List<String> feedback;
    private List<AiRepBreakdownDto> reps;
    private Map<String, AiMovementMetricDto> movementMetrics;
    private List<AiStructuredIssueDto> issues;
    private List<String> recommendations;
    private AiCameraQualityDto cameraQuality;
    private QuotaEntitlementResponseDto quota;
    private String errorMessage;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;

    @JsonIgnore
    @Builder.Default
    private boolean idempotentReplay = false;
}
