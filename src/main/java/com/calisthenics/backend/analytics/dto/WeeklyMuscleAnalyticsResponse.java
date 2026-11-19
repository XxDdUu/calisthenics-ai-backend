package com.calisthenics.backend.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyMuscleAnalyticsResponse {
    private String startDate;
    private String endDate;
    private int totalCompletedWorkouts;
    private int totalCompletedSets;
    private double totalEffectiveSets;

    @Builder.Default
    private List<MuscleVolumeDistributionDto> muscles = new ArrayList<>();

    @Builder.Default
    private Map<String, Double> regionDistribution = new LinkedHashMap<>();

    @Builder.Default
    private List<String> undertrainedMuscles = new ArrayList<>();

    @Builder.Default
    private List<String> optimalMuscles = new ArrayList<>();

    @Builder.Default
    private List<String> overloadedMuscles = new ArrayList<>();

    @Builder.Default
    private List<String> caveats = new ArrayList<>();
}
