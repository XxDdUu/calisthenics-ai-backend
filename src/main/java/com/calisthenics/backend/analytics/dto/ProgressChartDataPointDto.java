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
public class ProgressChartDataPointDto {
    private LocalDate date;
    private BigDecimal value; // Primary metric: external weight, reps, or hold duration
    private BigDecimal secondaryValue; // e.g. reps if weighted
    private BigDecimal volume;
    private Integer reps;
    private Integer holdDurationSeconds;
    private boolean isPersonalRecord;
}

