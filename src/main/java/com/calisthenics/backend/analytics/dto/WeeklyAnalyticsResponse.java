package com.calisthenics.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeeklyAnalyticsResponse {
    private String startDate;
    private String endDate;

    private int currentWeekWorkouts;
    private int previousWeekWorkouts;
    private BigDecimal workoutsChangePercentage;

    private int currentWeekSets;
    private int previousWeekSets;
    private BigDecimal setsChangePercentage;

    private int currentWeekReps;
    private int previousWeekReps;
    private BigDecimal repsChangePercentage;

    private BigDecimal currentWeekVolume;
    private BigDecimal previousWeekVolume;
    private BigDecimal volumeChangePercentage;

    private long currentWeekDurationSeconds;
    private long previousWeekDurationSeconds;
    private BigDecimal durationChangePercentage;

    private int exercisesPerformed;

    @Builder.Default
    private Map<String, Integer> dailyWorkouts = new HashMap<>();

    @JsonProperty("totalWorkouts")
    public long getTotalWorkouts() {
        return currentWeekWorkouts;
    }

    @JsonProperty("totalSets")
    public long getTotalSets() {
        return currentWeekSets;
    }

    @JsonProperty("totalReps")
    public long getTotalReps() {
        return currentWeekReps;
    }

    @JsonProperty("totalVolumeKg")
    public double getTotalVolumeKg() {
        return currentWeekVolume != null ? currentWeekVolume.doubleValue() : 0.0;
    }

    @JsonProperty("totalDurationSeconds")
    public long getTotalDurationSeconds() {
        return currentWeekDurationSeconds;
    }

    @JsonProperty("activeDays")
    public int getActiveDays() {
        return (int) dailyWorkouts.values().stream().filter(v -> v != null && v > 0).count();
    }

    @JsonProperty("workoutsPerDay")
    public Map<String, Integer> getWorkoutsPerDay() {
        return dailyWorkouts;
    }

    @JsonProperty("comparisonVsPreviousWeek")
    public Map<String, Double> getComparisonVsPreviousWeek() {
        Map<String, Double> map = new LinkedHashMap<>();
        if (volumeChangePercentage != null) {
            map.put("volumeImprovementPercent", volumeChangePercentage.doubleValue());
        }
        map.put("workoutsDifference", (double) (currentWeekWorkouts - previousWeekWorkouts));
        return map;
    }
}


