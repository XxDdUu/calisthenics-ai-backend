package com.calisthenics.backend.chat.controller;

import com.calisthenics.backend.chat.dto.request.CreateChatConversationRequest;
import com.calisthenics.backend.chat.dto.request.SendChatMessageRequest;
import com.calisthenics.backend.chat.dto.response.ChatConversationResponse;
import com.calisthenics.backend.chat.dto.response.ChatMessageResponse;
import com.calisthenics.backend.chat.dto.response.UnreadCountResponse;
import com.calisthenics.backend.chat.service.ChatMessageService;
import com.calisthenics.backend.chat.service.ChatService;
import com.calisthenics.backend.security.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ChatMessageService chatMessageService;

    @GetMapping("/conversations")
    public ResponseEntity<List<ChatConversationResponse>> listConversations(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(chatService.listConversations(currentUser.getId()));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ChatConversationResponse> createOrGetConversation(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateChatConversationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatService.getOrCreateConversation(currentUser.getId(), request.getOtherUserId()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<ChatMessageResponse>> listMessages(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "asc") String direction) {
        return ResponseEntity.ok(chatMessageService.listMessages(currentUser.getId(), conversationId, page, size, direction));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<ChatMessageResponse> sendMessage(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long conversationId,
            @Valid @RequestBody SendChatMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(chatMessageService.sendMessage(currentUser.getId(), conversationId, request));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Void> markAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long conversationId) {
        chatMessageService.markConversationAsRead(currentUser.getId(), conversationId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        return ResponseEntity.ok(chatService.getUnreadCount(currentUser.getId()));
    }
}
