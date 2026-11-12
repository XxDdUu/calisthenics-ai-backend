package com.calisthenics.backend.aichat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiConversationSummaryResponse {

    private Long id;
    private String title;
    private String lastMessagePreview;
    private String lastMessageRole;
    private ZonedDateTime lastInteractionAt;
    private ZonedDateTime createdAt;
    private int messageCount;
}
