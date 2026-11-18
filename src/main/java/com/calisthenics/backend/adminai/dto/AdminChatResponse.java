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
import com.calisthenics.backend.adminai.dto.data.ExerciseDraftBatchResultResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignAnalyticsResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignSummaryResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramPreviewResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private List<AdminRecommendationResponse> recommendations;
    private AdminPlatformOverviewResponse platformOverview;
    private AdminTrainingAnalyticsResponse trainingAnalytics;
    private List<AdminToolExecutionRecord> executedTools;
    private AdminTimeRangeMetadata timeRangeUsed;
    private AdminGlobalUserStatisticsResponse userStatistics;
    private List<AdminGlobalUserSummaryDto> matchedUsers;
    private AdminUserDetailDossierResponse inspectedUserDetail;
    private AdminUserSegmentsResponse segmentAnalysis;
    private AdminPlatformEngagementResponse platformEngagement;
    private AdminCoachClientStatisticsResponse coachClientStatistics;
    private GettingStartedProgramResponse gettingStartedProgram;
    private GettingStartedProgramPreviewResponse gettingStartedPreview;
    private List<GettingStartedCampaignSummaryResponse> activeCampaigns;
    private GettingStartedCampaignAnalyticsResponse campaignAnalytics;
    private AdminPendingConfirmationResponse pendingConfirmation;
    private AdminUserResponse updatedProUser;
    private List<AdminUserResponse> proCandidates;
    private List<AdminExerciseResponse> createdExercises;
    private ExerciseDraftBatchResultResponse exerciseDraftResult;
    private List<String> missingDataNotes;
    private List<String> suggestedFollowUps;
}
