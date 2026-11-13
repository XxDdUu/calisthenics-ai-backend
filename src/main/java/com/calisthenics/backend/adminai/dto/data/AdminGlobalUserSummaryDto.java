package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminGlobalUserSummaryDto {
    private Long userId;
    private String fullName;
    private String displayName;
    private String maskedEmail;
    /** Populated only when explicitly authorized and requested with includeEmail=true */
    private String email;
    private String role;
    /** CLIENT, COACH, ADMIN */
    private String accountCategory;
    private String experienceLevel;
    private String trainingExperience;
    private String primaryGoals;
    private String trainingInterests;
    private String preferredFrequency;
    private ZonedDateTime registeredAt;
    private ZonedDateTime lastActivityAt;
    /** ACTIVE, AT_RISK, INACTIVE */
    private String activityStatus;
    private Long daysSinceLastWorkout;
    private long totalWorkouts;
    private long completedWorkouts;
    private long abandonedOrIncompleteWorkouts;
    private double workoutCompletionRate;
    private long totalPersonalRecords;
    private Double bestPrImprovementPercent;
    private ZonedDateTime lastPrAchievedAt;
    /** IMPROVING, STABLE, DECLINING, INSUFFICIENT_DATA */
    private String consistencyTrend;
    /** HAS_ACTIVE_COACH, PENDING_INVITATION, NO_COACH, TERMINATED, COACH_ROLE */
    private String coachRelationshipStatus;
    private long coachCount;
    private long activeClientCount;
    private long aiConversationsCount;
    private long chatMessagesSentCount;
}
