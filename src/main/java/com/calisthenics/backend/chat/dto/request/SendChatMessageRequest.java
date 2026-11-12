package com.calisthenics.backend.chat.dto.request;

import com.calisthenics.backend.chat.type.ChatMessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SendChatMessageRequest {

    private Long conversationId;

    @NotBlank(message = "Message content is required")
    @Size(max = 4000, message = "Message content must be 4000 characters or fewer")
    private String content;

    private ChatMessageType messageType;

    @Size(max = 64, message = "Client message ID must be 64 characters or fewer")
    private String clientMessageId;
}
