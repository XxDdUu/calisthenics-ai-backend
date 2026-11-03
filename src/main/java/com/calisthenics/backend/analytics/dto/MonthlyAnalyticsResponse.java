package com.calisthenics.backend.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MonthlyAnalyticsResponse {
    private int completedWorkouts;
    private double trainingFrequency;
    private BigDecimal totalVolume;
    private BigDecimal volumeChangePercentage;
    private int personalRecordsAchieved;
    private int activeDays;
}

