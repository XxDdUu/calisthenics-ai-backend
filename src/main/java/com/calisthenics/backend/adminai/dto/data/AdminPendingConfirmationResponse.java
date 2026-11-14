package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import com.calisthenics.backend.adminai.type.AdminPendingActionType;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminPendingConfirmationResponse {
    private String confirmationToken;
    private AdminPendingActionType actionType;
    private AdminPendingActionStatus status;
    private Long programId;
    private String programTitle;
    private TargetAudience targetAudience;
    private String audienceLabel;
    private long eligibleAthleteCount;
    private String summary;
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
}
