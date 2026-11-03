package com.calisthenics.backend.chat.controller;

import com.calisthenics.backend.chat.dto.request.SendChatMessageRequest;
import com.calisthenics.backend.chat.dto.response.ChatMessageResponse;
import com.calisthenics.backend.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageService chatMessageService;

    @MessageMapping("/chat.send")
    public ChatMessageResponse sendMessage(@Payload SendChatMessageRequest request, Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new IllegalStateException("Authenticated websocket principal is required");
        }

        Long userId = Long.parseLong(principal.getName());
        if (request.getConversationId() == null) {
            throw new IllegalArgumentException("Conversation ID is required for websocket messaging");
        }

        return chatMessageService.sendMessage(userId, request.getConversationId(), request);
    }
}
