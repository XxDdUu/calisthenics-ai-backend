package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPerformanceSummaryDto {
    private String overallAssessment;
    private List<String> strengths;
    private List<String> weaknesses;
    private List<String> recommendations;
    private String nextSessionFocus;
    private String modelUsed;
    private Integer inputTokens;
    private Integer outputTokens;
    private Long latencyMs;
    private String status; // COMPLETED, UNAVAILABLE, PENDING
    private Instant generatedAt;
}
