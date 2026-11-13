package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminGlobalUserStatisticsResponse {
    private AdminTimeRangeMetadata timeRange;
    private long totalRegisteredUsers;
    private long totalRegularUsers;
    private long totalAdmins;
    private long totalCoaches;
    private long verifiedCoaches;
    private long totalClients;
    private long newRegistrationsInPeriod;
    private long activeUsersToday;
    private long activeUsers7Days;
    private long activeUsers30Days;
    private long activeUsersInRange;
    private long inactiveUsers14PlusDays;
    private long atRiskUsers7To14Days;
    private long usersWithActiveCoach;
    private long usersWithPendingCoachInvite;
    private long usersWithoutCoach;
    private long totalWorkoutsInRange;
    private long completedWorkoutsInRange;
    private long incompleteOrAbandonedWorkoutsInRange;
    private double overallWorkoutCompletionRate;
    private long totalPersonalRecordsAllTime;
    private long newPersonalRecordsInRange;
    private long usersWithStalePrs30PlusDays;
    private List<BucketCountDto> experienceLevelDistribution;
    private List<BucketCountDto> activityStatusDistribution;
    private List<BucketCountDto> topFitnessGoals;
    private List<String> missingDataNotes;
}
