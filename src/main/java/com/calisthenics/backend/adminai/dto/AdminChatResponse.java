package com.calisthenics.backend.adminai.dto;

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
    private List<String> suggestedFollowUps;
}
