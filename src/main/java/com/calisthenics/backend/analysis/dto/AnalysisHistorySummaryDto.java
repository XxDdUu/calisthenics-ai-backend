package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisHistorySummaryDto {
    private Long totalSessions;
    private Long totalReps;
    private BigDecimal averageFormScore;
    private BigDecimal bestFormScore;
    private BigDecimal trainingDurationSeconds;
}
