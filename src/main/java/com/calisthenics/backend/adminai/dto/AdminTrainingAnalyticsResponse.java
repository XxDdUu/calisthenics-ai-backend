package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminTrainingAnalyticsResponse {

    private AdminAnalyticsRange range;
    private LocalDate startDate;
    private LocalDate endDate;

    @Builder.Default
    private List<ExerciseUsageStatDto> mostPerformedExercises = new ArrayList<>();
    @Builder.Default
    private List<ExerciseUsageStatDto> highestCompletionRateExercises = new ArrayList<>();
    @Builder.Default
    private List<SkillFocusStatDto> skillFocusBreakdown = new ArrayList<>();
    @Builder.Default
    private List<GoalStatDto> commonTrainingGoals = new ArrayList<>();
    private InactiveUsersSummaryDto inactiveUsersSummary;
    private PlanAdherenceSummaryDto planAdherenceSummary;
    @Builder.Default
    private List<ProgramEngagementStatDto> topEngagedPrograms = new ArrayList<>();
    @Builder.Default
    private Map<String, Long> exerciseLibraryByCategory = new LinkedHashMap<>();
    @Builder.Default
    private Map<String, Long> exerciseLibraryByDifficulty = new LinkedHashMap<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExerciseUsageStatDto {
        private Long exerciseId;
        private String exerciseName;
        private String category;
        private String difficulty;
        private long sessionPerformances;
        private long completedPerformances;
        private long planAssignmentsCount;
        private long prCount;
        private double completionRatePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillFocusStatDto {
        private String skillName;
        private long existingExerciseCount;
        private long planReferencesCount;
        private long sessionPerformancesCount;
        private long prCount;
        private long interestedUsersCount;
        @Builder.Default
        private List<String> missingProgressions = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GoalStatDto {
        private String goal;
        private long userCount;
        private double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InactiveUsersSummaryDto {
        private long totalUsersEvaluated;
        private long inactiveOver14DaysCount;
        private long inactiveOver30DaysCount;
        private long neverCompletedWorkoutCount;
        private double inactiveRatePercent;
        @Builder.Default
        private List<InactiveUserItemDto> inactiveUserSamples = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class InactiveUserItemDto {
        private Long userId;
        private String fullName;
        private String role;
        private LocalDate lastWorkoutDate;
        private long daysSinceLastActivity;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanAdherenceSummaryDto {
        private long usersWithActivePersonalPlan;
        private long activeCoachAssignmentsCount;
        private long completedAssignedSessions;
        private long totalAssignedSessions;
        private double adherenceRatePercent;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramEngagementStatDto {
        private Long planId;
        private String planName;
        private String splitType;
        private String ownerName;
        private boolean aiGenerated;
        private long completedSessionsCount;
        private long totalSessionsCount;
        private double completionRatePercent;
    }
}
