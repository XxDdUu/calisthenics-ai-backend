package com.calisthenics.backend.chat.service.impl;

import com.calisthenics.backend.chat.dto.response.ChatConversationResponse;
import com.calisthenics.backend.chat.dto.response.UnreadCountResponse;
import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.entity.ChatMessage;
import com.calisthenics.backend.chat.exception.ChatConversationNotFoundException;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.chat.repository.ChatMessageRepository;
import com.calisthenics.backend.chat.service.ChatService;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final CoachClientRelationshipRepository relationshipRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public ChatConversationResponse getOrCreateConversation(Long currentUserId, Long otherUserId) {
        if (currentUserId.equals(otherUserId)) {
            throw new IllegalArgumentException("A user cannot start a conversation with themselves");
        }

        User currentUser = userRepository.findById(currentUserId)
                .orElseThrow(ChatConversationNotFoundException::new);
        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(ChatConversationNotFoundException::new);

        CoachClientRelationship relationship = relationshipRepository.findActiveRelationshipForUsers(currentUserId, otherUserId)
                .orElseThrow(() -> new IllegalArgumentException("Only active coach-client relationships can use chat"));

        Long coachId = relationship.getCoach().getId();
        Long clientId = relationship.getClient().getId();

        ChatConversation conversation = conversationRepository.findByCoachIdAndClientId(coachId, clientId)
                .orElseGet(() -> conversationRepository.save(ChatConversation.builder()
                        .coach(relationship.getCoach())
                        .client(relationship.getClient())
                        .coachLastReadAt(currentUserId.equals(coachId) ? ZonedDateTime.now() : null)
                        .clientLastReadAt(currentUserId.equals(clientId) ? ZonedDateTime.now() : null)
                        .build()));

        return toConversationResponse(conversation, currentUserId, currentUser, otherUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatConversationResponse> listConversations(Long currentUserId) {
        return conversationRepository.findByCoachIdOrClientIdOrderByLastMessageAtDesc(currentUserId).stream()
                .map(conversation -> {
                    User otherUser = currentUserId.equals(conversation.getCoach().getId())
                            ? conversation.getClient()
                            : conversation.getCoach();
                    return toConversationResponse(conversation, currentUserId, currentUserId.equals(conversation.getCoach().getId()) ? conversation.getCoach() : conversation.getClient(), otherUser);
                })
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UnreadCountResponse getUnreadCount(Long currentUserId) {
        List<ChatConversation> conversations = conversationRepository.findByCoachIdOrClientIdOrderByLastMessageAtDesc(currentUserId);
        long unread = 0;
        for (ChatConversation conversation : conversations) {
            ZonedDateTime readAt = currentUserId.equals(conversation.getCoach().getId())
                    ? conversation.getCoachLastReadAt()
                    : conversation.getClientLastReadAt();
            unread += messageRepository.countUnreadMessages(conversation.getId(), currentUserId, readAt);
        }
        return new UnreadCountResponse(unread);
    }

    private ChatConversationResponse toConversationResponse(ChatConversation conversation, Long currentUserId, User currentUser, User otherUser) {
        Long otherUserId = currentUserId.equals(conversation.getCoach().getId()) ? conversation.getClient().getId() : conversation.getCoach().getId();
        String otherUserName = otherUser.getFullName();
        ZonedDateTime readAt = currentUserId.equals(conversation.getCoach().getId())
                ? conversation.getCoachLastReadAt()
                : conversation.getClientLastReadAt();
        long unreadCount = messageRepository.countUnreadMessages(conversation.getId(), currentUserId, readAt);

        return ChatConversationResponse.builder()
                .id(conversation.getId())
                .otherUserId(otherUserId)
                .otherUserName(otherUserName)
                .otherUserAvatarUrl(otherUser.getAvatarUrl())
                .lastMessagePreview(messageRepository.findTopByConversationOrderByCreatedAtDesc(conversation)
                        .map(ChatMessage::getContent)
                        .orElse(null))
                .unreadCount(unreadCount)
                .lastMessageAt(conversation.getLastMessageAt())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }
}
