package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSegmentFilter {
    private String searchQuery;
    private String experienceLevel;
    private String fitnessGoal;
    private String preferredFrequency;
    /** ACTIVE, AT_RISK, INACTIVE */
    private String activityStatus;
    private Integer inactiveDaysThreshold;
    private Double minCompletionRate;
    private Double maxCompletionRate;
    /** IMPROVING, STALE, NONE */
    private String prProgressionStatus;
    private Integer stalePrDaysThreshold;
    /** HAS_ACTIVE_COACH, NO_COACH, PENDING_INVITATION, COACH_ROLE, TERMINATED */
    private String coachRelationshipStatus;
    /** ALL, CLIENT, COACH, ADMIN */
    private String roleFilter;
    private LocalDate registeredFrom;
    private LocalDate registeredTo;
    private String skillKeyword;
    private String naturalLanguageQuery;
    @Builder.Default
    private Boolean includeEmail = false;
    /** RECENT_ACTIVITY, COMPLETION_RATE_DESC, COMPLETION_RATE_ASC, PR_IMPROVEMENT_DESC, CREATED_AT_DESC, WORKOUT_COUNT_DESC */
    private String sortBy;
    private AdminAnalyticsRange range;
    private LocalDate startDate;
    private LocalDate endDate;
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 20;
}
