package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlatformOverviewResponse {

    private AdminAnalyticsRange range;
    private LocalDate startDate;
    private LocalDate endDate;

    private long totalRegisteredUsers;
    private long activeUsersInPeriod;
    private long newRegistrationsInPeriod;

    private long totalCoaches;
    private long totalClients;
    private long totalAdmins;
    private long activeCoachClientRelationships;
    private long pendingCoachClientRelationships;

    private long totalWorkoutPlans;
    private long activeWorkoutPlans;
    private long aiGeneratedWorkoutPlans;

    private long totalCompletedWorkoutsAllTime;
    private long completedWorkoutsInPeriod;
    private long scheduledOrInProgressWorkoutsInPeriod;

    private long totalRecordedPrsAllTime;
    private long recordedPrsInPeriod;

    private long totalExercises;
    private long activeExercises;

    private EngagementMetricsDto engagementMetrics;
    private ActivityTrendSummaryDto activityTrends;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EngagementMetricsDto {
        private double activeUserRatePercent;
        private double workoutCompletionRatePercent;
        private double avgWorkoutsPerActiveUser;
        private long usersWithActivePlanCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActivityTrendSummaryDto {
        private long previousPeriodCompletedWorkouts;
        private long previousPeriodNewUsers;
        private long previousPeriodPrs;
        private double workoutGrowthPercent;
        private double userGrowthPercent;
        private List<DailyTrendPointDto> dailyPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrendPointDto {
        private LocalDate date;
        private long completedWorkouts;
        private long newUsers;
        private long recordedPrs;
    }
}
