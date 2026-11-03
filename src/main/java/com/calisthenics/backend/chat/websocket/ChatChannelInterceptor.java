package com.calisthenics.backend.chat.websocket;

import com.calisthenics.backend.chat.exception.ChatAccessDeniedException;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class ChatChannelInterceptor implements ChannelInterceptor {

    private final JwtTokenProvider jwtTokenProvider;
    private final ChatConversationRepository conversationRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand()) &&
                "/app/presence.heartbeat".equals(accessor.getDestination()) &&
                accessor.getUser() == null) {
            throw new ChatAccessDeniedException();
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        if (accessor.getUser() != null) {
            return;
        }

        String authHeader = accessor.getFirstNativeHeader("Authorization");
        if (authHeader == null) {
            authHeader = accessor.getFirstNativeHeader("token");
        }
        if (authHeader == null) {
            authHeader = accessor.getPasscode();
        }

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authHeader = authHeader.substring(7).trim();
        }

        if (authHeader != null && jwtTokenProvider.validateToken(authHeader)) {
            Long userId = jwtTokenProvider.getUserIdFromJWT(authHeader);
            Principal principal = () -> String.valueOf(userId);
            accessor.setUser(principal);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }

        Principal principal = accessor.getUser();
        if (principal == null || principal.getName() == null) {
            throw new ChatAccessDeniedException();
        }

        if (destination.startsWith("/user/") &&
                !"/user/queue/messages".equals(destination) &&
                !"/user/queue/presence".equals(destination)) {
            throw new ChatAccessDeniedException();
        }
        if (destination.startsWith("/topic/presence")) {
            throw new ChatAccessDeniedException();
        }

        Long currentUserId;
        try {
            currentUserId = Long.parseLong(principal.getName());
        } catch (NumberFormatException ex) {
            throw new ChatAccessDeniedException();
        }

        if (destination.startsWith("/topic/conversations/")) {
            String idStr = destination.substring("/topic/conversations/".length());
            try {
                Long conversationId = Long.parseLong(idStr);
                boolean isParticipant = conversationRepository.findByIdAndParticipant(conversationId, currentUserId).isPresent();
                if (!isParticipant) {
                    log.warn("Subscription denied: user {} is not participant in conversation {}", currentUserId, conversationId);
                    throw new ChatAccessDeniedException();
                }
            } catch (NumberFormatException ex) {
                throw new ChatAccessDeniedException();
            }
        }
    }
}
