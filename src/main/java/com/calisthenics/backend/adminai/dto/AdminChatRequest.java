package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminChatRequest {
    private Long conversationId;
    @NotBlank(message = "Message cannot be blank")
    private String message;
    private AdminAnalyticsRange range;
    private LocalDate startDate;
    private LocalDate endDate;
    private Long targetUserId;
    private Boolean includeEmail;
    private String idempotencyKey;
}
