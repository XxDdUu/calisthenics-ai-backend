package com.calisthenics.backend.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgressiveOverloadComparisonDto {
    private boolean hasComparison;
    private LocalDate latestDate;
    private LocalDate previousDate;
    private BigDecimal weightDiff;
    private BigDecimal weightPercentage;
    private Integer repsDiff;
    private BigDecimal repsPercentage;
    private Integer durationDiff;
    private BigDecimal durationPercentage;
    private BigDecimal volumeDiff;
    private BigDecimal volumePercentage;
    private String summaryText;
}

