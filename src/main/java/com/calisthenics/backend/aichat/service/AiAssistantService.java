package com.calisthenics.backend.aichat.service;

import com.calisthenics.backend.aichat.dto.*;

import java.util.List;

public interface AiAssistantService {

    List<AiConversationSummaryResponse> getUserConversations(Long userId);

    AiConversationSummaryResponse getLatestConversationSummary(Long userId);

    AiConversationDetailResponse getConversation(Long userId, Long conversationId);

    AiConversationSummaryResponse createConversation(Long userId, CreateAiConversationRequest request);

    void deleteConversation(Long userId, Long conversationId);

    AiMessageResponse sendMessage(Long userId, Long conversationId, SendAiMessageRequest request);

    AiMessageResponse sendMessageToActiveConversation(Long userId, SendAiMessageRequest request);
}
