package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.admin.dto.AdminUserResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminProToolResultResponse {
    private String action;
    private String outcomeStatus;
    private boolean confirmationRequired;
    private boolean executed;
    private String message;
    private AdminPendingConfirmationResponse pendingConfirmation;
    private AdminUserResponse targetUser;
    private List<AdminUserResponse> candidates;
    private Long targetUserId;
}
