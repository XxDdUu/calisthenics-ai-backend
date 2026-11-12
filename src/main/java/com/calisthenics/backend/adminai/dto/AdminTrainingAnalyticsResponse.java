package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
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

    private List<ExerciseUsageStatDto> mostPerformedExercises;
    private List<ExerciseUsageStatDto> highestCompletionRateExercises;
    private List<SkillFocusStatDto> skillFocusBreakdown;
    private List<GoalStatDto> commonTrainingGoals;
    private InactiveUsersSummaryDto inactiveUsersSummary;
    private PlanAdherenceSummaryDto planAdherenceSummary;
    private List<ProgramEngagementStatDto> topEngagedPrograms;
    private Map<String, Long> exerciseLibraryByCategory;
    private Map<String, Long> exerciseLibraryByDifficulty;

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
        private List<String> missingProgressions;
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
        private List<InactiveUserItemDto> inactiveUserSamples;
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
