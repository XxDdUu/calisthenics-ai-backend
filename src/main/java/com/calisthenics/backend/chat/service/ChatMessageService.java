package com.calisthenics.backend.chat.service;

import com.calisthenics.backend.chat.dto.request.SendChatMessageRequest;
import com.calisthenics.backend.chat.dto.response.ChatMessageResponse;

import java.util.List;

public interface ChatMessageService {
    List<ChatMessageResponse> listMessages(Long currentUserId, Long conversationId, int page, int size);
    List<ChatMessageResponse> listMessages(Long currentUserId, Long conversationId, int page, int size, String direction);
    ChatMessageResponse sendMessage(Long currentUserId, Long conversationId, SendChatMessageRequest request);
    void markConversationAsRead(Long currentUserId, Long conversationId);
}
