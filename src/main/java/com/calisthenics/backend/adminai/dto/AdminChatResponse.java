package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import com.calisthenics.backend.admin.dto.AdminUserResponse;
import com.calisthenics.backend.adminai.dto.data.AdminCoachClientStatisticsResponse;
import com.calisthenics.backend.adminai.dto.data.AdminGlobalUserStatisticsResponse;
import com.calisthenics.backend.adminai.dto.data.AdminGlobalUserSummaryDto;
import com.calisthenics.backend.adminai.dto.data.AdminPendingConfirmationResponse;
import com.calisthenics.backend.adminai.dto.data.AdminPlatformEngagementResponse;
import com.calisthenics.backend.adminai.dto.data.AdminTimeRangeMetadata;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecutionRecord;
import com.calisthenics.backend.adminai.dto.data.AdminUserDetailDossierResponse;
import com.calisthenics.backend.adminai.dto.data.AdminUserSegmentsResponse;
import com.calisthenics.backend.adminai.dto.data.BulkExerciseGenerationJobResponse;
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftBatchResultResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignAnalyticsResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignSummaryResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramPreviewResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminChatResponse {
    private Long conversationId;
    private String conversationTitle;
    private AdminMessageResponse userMessage;
    private AdminMessageResponse assistantMessage;
    private String intent;
    @Builder.Default
    private List<AdminRecommendationResponse> recommendations = new ArrayList<>();
    private AdminPlatformOverviewResponse platformOverview;
    private AdminTrainingAnalyticsResponse trainingAnalytics;
    @Builder.Default
    private List<AdminToolExecutionRecord> executedTools = new ArrayList<>();
    private AdminTimeRangeMetadata timeRangeUsed;
    private AdminGlobalUserStatisticsResponse userStatistics;
    @Builder.Default
    private List<AdminGlobalUserSummaryDto> matchedUsers = new ArrayList<>();
    private AdminUserDetailDossierResponse inspectedUserDetail;
    private AdminUserSegmentsResponse segmentAnalysis;
    private AdminPlatformEngagementResponse platformEngagement;
    private AdminCoachClientStatisticsResponse coachClientStatistics;
    private GettingStartedProgramResponse gettingStartedProgram;
    private GettingStartedProgramPreviewResponse gettingStartedPreview;
    @Builder.Default
    private List<GettingStartedCampaignSummaryResponse> activeCampaigns = new ArrayList<>();
    private GettingStartedCampaignAnalyticsResponse campaignAnalytics;
    private AdminPendingConfirmationResponse pendingConfirmation;
    private AdminUserResponse updatedProUser;
    @Builder.Default
    private List<AdminUserResponse> proCandidates = new ArrayList<>();
    @Builder.Default
    private List<AdminExerciseResponse> createdExercises = new ArrayList<>();
    private ExerciseDraftBatchResultResponse exerciseDraftResult;
    private BulkExerciseGenerationJobResponse bulkGenerationJob;
    @Builder.Default
    private List<String> missingDataNotes = new ArrayList<>();
    @Builder.Default
    private List<String> suggestedFollowUps = new ArrayList<>();

    /** Ensures collection fields are never null on serialization even when built from partial tool state or cached JSON. */
    public AdminChatResponse normalizeCollections() {
        if (recommendations == null) recommendations = Collections.emptyList();
        if (executedTools == null) executedTools = Collections.emptyList();
        if (matchedUsers == null) matchedUsers = Collections.emptyList();
        if (activeCampaigns == null) activeCampaigns = Collections.emptyList();
        if (proCandidates == null) proCandidates = Collections.emptyList();
        if (createdExercises == null) createdExercises = Collections.emptyList();
        if (missingDataNotes == null) missingDataNotes = Collections.emptyList();
        if (suggestedFollowUps == null) suggestedFollowUps = Collections.emptyList();
        return this;
    }
}
