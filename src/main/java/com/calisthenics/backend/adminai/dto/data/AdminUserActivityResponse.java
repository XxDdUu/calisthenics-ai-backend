package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserActivityResponse {
    private AdminTimeRangeMetadata timeRange;
    private Long userId;
    private String fullName;
    private String presenceStatus;
    private ZonedDateTime lastSeenAt;
    private ZonedDateTime lastWorkoutAt;
    private ZonedDateTime lastChatMessageAt;
    private ZonedDateTime lastAiInteractionAt;
    private ZonedDateTime lastVideoAnalysisAt;
    private ZonedDateTime lastLiveAnalysisAt;
    private long chatConversationsCount;
    private long chatMessagesSentTotal;
    private long chatMessagesSentInRange;
    private long aiConversationsCount;
    private long aiMessagesTotal;
    private long aiMessagesInRange;
    private long workoutPlannerRunsTotal;
    private long workoutPlannerRunsInRange;
    private long workoutPlannerSuccessCount;
    private long liveAnalysisSessionsInRange;
    private long videoAnalysesInRange;
    private long prRecordsLoggedInRange;
    @Builder.Default
    private List<BucketCountDto> topAiIntents = new ArrayList<>();
    private String privacyNotice;
    @Builder.Default
    private List<String> missingDataNotes = new ArrayList<>();
}
