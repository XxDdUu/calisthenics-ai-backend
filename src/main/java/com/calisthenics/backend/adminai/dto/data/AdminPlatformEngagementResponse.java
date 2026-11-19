package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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
    @Builder.Default
    private List<ExperienceComparisonItem> experienceComparisons = new ArrayList<>();
    @Builder.Default
    private List<ExerciseUsageItem> topPerformedExercises = new ArrayList<>();
    @Builder.Default
    private List<SkillImproverItem> topSkillImprovers = new ArrayList<>();
    @Builder.Default
    private List<AdminGlobalUserSummaryDto> decliningConsistencyUsers = new ArrayList<>();
    @Builder.Default
    private List<AdminGlobalUserSummaryDto> stalePrUsers = new ArrayList<>();
    private ChatAiUsageSummary chatAndAiUsageSummary;
    @Builder.Default
    private List<String> missingDataNotes = new ArrayList<>();

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
        @Builder.Default
        private List<BucketCountDto> topAiIntents = new ArrayList<>();
    }
}
