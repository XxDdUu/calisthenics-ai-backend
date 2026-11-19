package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.admin.dto.AdminUserResponse;
import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import com.calisthenics.backend.adminai.type.AdminPendingActionType;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignSummaryResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminConfirmationDecisionResponse {
    private String confirmationToken;
    private AdminPendingActionType actionType;
    private AdminPendingActionStatus status;
    private Long conversationId;
    private String reply;
    private AdminToolExecutionRecord execution;
    private GettingStartedProgramResponse gettingStartedProgram;
    @Builder.Default
    private List<GettingStartedCampaignSummaryResponse> activeCampaigns = new ArrayList<>();
    private AdminUserResponse updatedUser;
}
