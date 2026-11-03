package com.calisthenics.backend.profile.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientTrainingOverviewResponse {

    private Long userId;
    private Long activePlansCount;
    private Long completedSessionsCount;
    private Long currentPlanId;
    private String currentPlanName;
    private ConnectedCoachSummary connectedCoach;
    private List<RecentWorkoutSummary> recentWorkouts;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentWorkoutSummary {
        private Long id;
        private String title;
        private ZonedDateTime startedAt;
        private ZonedDateTime completedAt;
    }
}
