package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisResponse {

    private Long id;
    private Long userId;
    private Long exerciseId;
    private String exerciseName;
    private String mediaUrl;
    private String mediaType;
    private BigDecimal overallScore;
    private BigDecimal holdDurationSeconds;
    private String formFeedback;
    private BigDecimal elbowAngle;
    private BigDecimal bodyLineAngle;
    private BigDecimal shoulderLeanAngle;
    private String rawKeypointsJson;
    private ZonedDateTime createdAt;
}
