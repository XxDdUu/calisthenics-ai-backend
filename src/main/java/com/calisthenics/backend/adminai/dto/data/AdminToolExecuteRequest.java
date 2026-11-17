package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedExerciseOperationRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminToolExecuteRequest {
    private String toolName;
    private Long userId;
    private String skillKeyword;
    private AdminUserSegmentFilter filter;
    private AdminAnalyticsRange range;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer page;
    private Integer size;
    private Boolean includeEmail;
    private String goal;

    // Getting Started – 3 Days Program & Campaign management fields
    private Long programId;
    private Long conversationId;
    private String title;
    private String description;
    private String targetAudience;
    private String instruction;
    private AdminGettingStartedProgramRequest program;
    private List<GettingStartedExerciseOperationRequest> operations;
    private Boolean confirmed;
    private String confirmationToken;

    // PRO entitlement management fields
    private String userIdentifier;
    private String reason;
}
