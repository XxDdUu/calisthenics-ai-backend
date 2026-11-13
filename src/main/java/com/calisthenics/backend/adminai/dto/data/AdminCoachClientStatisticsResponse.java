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
public class AdminCoachClientStatisticsResponse {
    private AdminTimeRangeMetadata timeRange;
    private long totalCoaches;
    private long publicCoaches;
    private long acceptingClientsCoaches;
    private long verifiedCoaches;
    private long totalRelationships;
    private long activeRelationships;
    private long pendingInvitations;
    private long rejectedRelationships;
    private long cancelledRelationships;
    private long terminatedRelationships;
    private long clientsWithActiveCoach;
    private long totalAssignedPlans;
    private long activeAssignedPlans;
    private long completedAssignedPlans;
    private long totalSharedPrs;
    private long sharedPrsWithFeedback;
    private double feedbackResponseRate;
    private List<CoachPerformanceItem> topCoaches;
    private List<AdminGlobalUserSummaryDto> unassignedCoachCandidates;
    private List<String> missingDataNotes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoachPerformanceItem {
        private Long coachUserId;
        private String coachName;
        private boolean verified;
        private boolean acceptingClients;
        private long activeClients;
        private long pendingInvitations;
        private long activeAssignedPlans;
        private long sharedPrFeedbackCount;
    }
}
