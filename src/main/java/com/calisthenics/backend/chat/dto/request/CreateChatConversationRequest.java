package com.calisthenics.backend.chat.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateChatConversationRequest {

    @NotNull(message = "Other user ID is required")
    @Positive(message = "Other user ID must be positive")
    private Long otherUserId;
}
