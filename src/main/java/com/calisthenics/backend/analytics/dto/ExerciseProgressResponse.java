package com.calisthenics.backend.analytics.dto;

import com.calisthenics.backend.exercise.muscle.ExerciseMuscleTargetDto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseProgressResponse {
    private Long exerciseId;
    private String exerciseName;
    private String category;
    private String difficulty;
    private String description;
    private String instructions;
    private String targetMuscles;
    private String secondaryMuscles;
    @Builder.Default
    private List<String> primaryMuscles = new ArrayList<>();
    @Builder.Default
    private List<String> primaryMuscleIds = new ArrayList<>();
    @Builder.Default
    private List<String> secondaryMuscleIds = new ArrayList<>();
    @Builder.Default
    private List<ExerciseMuscleTargetDto> muscleTargets = new ArrayList<>();
    private int totalCompletedSessions;
    private int totalSets;
    private int totalReps;
    private int totalHoldDurationSeconds;
    private String bestSet;
    private LocalDate lastPerformedDate;
    @Builder.Default
    private List<PersonalRecordResponse> personalRecords = new ArrayList<>();
    private ProgressiveOverloadComparisonDto latestVsPrevious;
    @Builder.Default
    private List<ProgressChartDataPointDto> chartData = new ArrayList<>();
    @Builder.Default
    private List<ExerciseHistorySessionDto> sessions = new ArrayList<>();

    @JsonProperty("exerciseCategory")
    public String getExerciseCategory() {
        return category;
    }

    @JsonProperty("totalSessionsCompleted")
    public long getTotalSessionsCompleted() {
        return totalCompletedSessions;
    }

    @JsonProperty("totalSetsCompleted")
    public long getTotalSetsCompleted() {
        return totalSets;
    }

    @JsonProperty("totalRepsCompleted")
    public long getTotalRepsCompleted() {
        return totalReps;
    }

    @JsonProperty("bestSetSummary")
    public String getBestSetSummary() {
        return bestSet;
    }

    @JsonProperty("overloadComparison")
    public ProgressiveOverloadComparisonDto getOverloadComparison() {
        return latestVsPrevious;
    }

    @JsonProperty("history")
    public List<ExerciseHistorySessionDto> getHistory() {
        return sessions;
    }
}


