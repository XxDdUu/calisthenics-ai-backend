package com.calisthenics.backend.chat.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.ZonedDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatConversationResponse {
    private Long id;
    private Long otherUserId;
    private String otherUserName;
    private String otherUserAvatarUrl;
    private String lastMessagePreview;
    private Long unreadCount;
    private ZonedDateTime lastMessageAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
