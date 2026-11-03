package com.calisthenics.backend.chat.service;

import com.calisthenics.backend.chat.dto.response.ChatConversationResponse;
import com.calisthenics.backend.chat.dto.response.UnreadCountResponse;

import java.util.List;

public interface ChatService {
    ChatConversationResponse getOrCreateConversation(Long currentUserId, Long otherUserId);
    List<ChatConversationResponse> listConversations(Long currentUserId);
    UnreadCountResponse getUnreadCount(Long currentUserId);
}
