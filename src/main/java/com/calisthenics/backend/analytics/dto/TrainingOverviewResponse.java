package com.calisthenics.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingOverviewResponse {
    private int totalWorkouts;
    private int totalActiveDays;
    private int currentStreak;
    private int longestStreak;
    private double weeklyFrequency;
    private int totalPRs;
    @Builder.Default
    private List<PersonalRecordResponse> recentPRs = new ArrayList<>();

    @JsonProperty("totalWorkoutsCompleted")
    public long getTotalWorkoutsCompleted() {
        return totalWorkouts;
    }

    @JsonProperty("currentStreakDays")
    public int getCurrentStreakDays() {
        return currentStreak;
    }

    @JsonProperty("longestStreakDays")
    public int getLongestStreakDays() {
        return longestStreak;
    }

    @JsonProperty("activeDaysThisMonth")
    public int getActiveDaysThisMonth() {
        return totalActiveDays;
    }

    @JsonProperty("totalPersonalRecords")
    public long getTotalPersonalRecords() {
        return totalPRs;
    }
}


