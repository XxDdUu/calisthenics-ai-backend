package com.calisthenics.backend.videoanalysis.dto;

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
    private String exerciseName;
    private String status; // PENDING, PROCESSING, COMPLETED, FAILED
    private BigDecimal overallScore;
    private BigDecimal formAccuracy;
    private BigDecimal stabilityScore;
    private BigDecimal alignmentScore;
    private Long durationMs;
    private Integer width;
    private Integer height;
    private BigDecimal fps;
    private String originalVideoUrl;
    private String annotatedVideoUrl;
    private String keypointsUrl;
    private Map<String, Object> metrics;
    private List<String> feedback;
    private String errorMessage;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
