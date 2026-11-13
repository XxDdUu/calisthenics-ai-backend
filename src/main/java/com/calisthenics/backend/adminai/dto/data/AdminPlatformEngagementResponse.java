package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPlatformEngagementResponse {
    private AdminTimeRangeMetadata timeRange;
    private long dauCount;
    private long wauCount;
    private long mauCount;
    private double stickinessDauToMauPercent;
    private List<ExperienceComparisonItem> experienceComparisons;
    private List<ExerciseUsageItem> topPerformedExercises;
    private List<SkillImproverItem> topSkillImprovers;
    private List<AdminGlobalUserSummaryDto> decliningConsistencyUsers;
    private List<AdminGlobalUserSummaryDto> stalePrUsers;
    private ChatAiUsageSummary chatAndAiUsageSummary;
    private List<String> missingDataNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExperienceComparisonItem {
        private String experienceLevel;
        private long userCount;
        private long activeUserCount;
        private double activeRatePercent;
        private long totalWorkouts;
        private long completedWorkouts;
        private double avgWorkoutsPerUser;
        private double avgCompletionRate;
        private long prCount;
        private double avgAiInteractionsPerUser;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExerciseUsageItem {
        private Long exerciseId;
        private String exerciseName;
        private String category;
        private String difficulty;
        private long totalPerformances;
        private long completedPerformances;
        private double completionRate;
        private long uniqueUsersCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillImproverItem {
        private Long userId;
        private String fullName;
        private String experienceLevel;
        private String exerciseName;
        private String recordType;
        private BigDecimal latestRecordValue;
        private BigDecimal previousRecordValue;
        private String unit;
        private BigDecimal improvementPercent;
        private ZonedDateTime achievedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChatAiUsageSummary {
        private long totalChatMessagesInRange;
        private long activeChatUsersInRange;
        private long totalAiMessagesInRange;
        private long activeAiUsersInRange;
        private long workoutPlannerRunsInRange;
        private double workoutPlannerSuccessRatePercent;
        private long prRecordsInRange;
        private long liveAnalysisSessionsInRange;
        private long videoAnalysesInRange;
        private List<BucketCountDto> topAiIntents;
    }
}
