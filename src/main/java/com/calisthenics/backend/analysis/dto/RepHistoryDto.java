package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RepHistoryDto {
    private Long id;
    private Integer repNumber;
    private Instant startedAt;
    private Instant completedAt;
    private Long durationMs;
    private Long timestampOffsetMs;
    private BigDecimal minimumKneeAngle;
    private BigDecimal minimumJointAngle;
    private BigDecimal minimumElbowAngle;
    private BigDecimal formScore;
    private String depthRating;
    private String completionStatus;
    private String feedback;
    private List<String> detectedErrors;
    private Map<String, Double> metrics;
}
