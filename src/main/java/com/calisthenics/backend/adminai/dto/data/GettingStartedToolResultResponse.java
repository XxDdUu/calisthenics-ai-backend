package com.calisthenics.backend.adminai.dto.data;

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
public class GettingStartedToolResultResponse {
    private String status;
    private boolean confirmationRequired;
    private String message;
    private GettingStartedProgramResponse program;
    private GettingStartedProgramPreviewResponse preview;
    private List<GettingStartedCampaignSummaryResponse> activeCampaigns;
    private GettingStartedCampaignAnalyticsResponse analytics;
    private AdminPendingConfirmationResponse pendingConfirmation;
    private Long eligibleAthleteCount;
}
