package com.calisthenics.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
public class AiAnalysisResultDto {

    @JsonProperty("overall_score")
    private BigDecimal overallScore;

    @JsonProperty("hold_duration_seconds")
    private BigDecimal holdDurationSeconds;

    @JsonProperty("elbow_angle")
    private BigDecimal elbowAngle;

    @JsonProperty("body_line_angle")
    private BigDecimal bodyLineAngle;

    @JsonProperty("shoulder_lean_angle")
    private BigDecimal shoulderLeanAngle;

    @JsonProperty("feedback")
    private List<String> feedback;

    @JsonProperty("stage")
    private String stage;

    @JsonProperty("keypoints")
    private Map<String, Object> keypoints;
}
