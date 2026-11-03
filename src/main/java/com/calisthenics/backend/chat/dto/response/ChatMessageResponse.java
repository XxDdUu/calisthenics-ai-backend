package com.calisthenics.backend.chat.dto.response;

import com.calisthenics.backend.chat.type.ChatMessageType;
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
public class ChatMessageResponse {
    private Long id;
    private Long conversationId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private String content;
    private ChatMessageType messageType;
    private String clientMessageId;
    private ZonedDateTime createdAt;
}
