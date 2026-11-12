package com.calisthenics.backend.chat.service.impl;

import com.calisthenics.backend.analytics.dto.SharedPersonalRecordResponse;
import com.calisthenics.backend.analytics.repository.SharedPersonalRecordRepository;
import com.calisthenics.backend.analytics.service.PersonalRecordService;
import com.calisthenics.backend.chat.dto.request.SendChatMessageRequest;
import com.calisthenics.backend.chat.dto.response.ChatMessageResponse;
import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.entity.ChatMessage;
import com.calisthenics.backend.chat.exception.ChatAccessDeniedException;
import com.calisthenics.backend.chat.exception.ChatConversationNotFoundException;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.chat.repository.ChatMessageRepository;
import com.calisthenics.backend.chat.service.ChatMessageService;
import com.calisthenics.backend.chat.type.ChatMessageType;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatMessageServiceImpl implements ChatMessageService {

    private final ChatConversationRepository conversationRepository;
    private final ChatMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired(required = false)
    private SharedPersonalRecordRepository sharedPersonalRecordRepository;

    @Autowired(required = false)
    private PersonalRecordService personalRecordService;

    @Autowired(required = false)
    private ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(Long currentUserId, Long conversationId, int page, int size) {
        return listMessages(currentUserId, conversationId, page, size, "asc");
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatMessageResponse> listMessages(Long currentUserId, Long conversationId, int page, int size, String direction) {
        ChatConversation conversation = conversationRepository.findByIdAndParticipant(conversationId, currentUserId)
                .orElseThrow(ChatConversationNotFoundException::new);

        List<ChatMessage> messages = "desc".equalsIgnoreCase(direction)
                ? messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, PageRequest.of(page, size))
                : messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId, PageRequest.of(page, size));

        return messages.stream()
                .map(message -> toResponse(conversation.getId(), message))
                .toList();
    }

    private ChatMessageResponse toResponse(Long conversationId, ChatMessage message) {
        SharedPersonalRecordResponse sharedPr = resolveSharedPrFromMessage(conversationId, message);
        return ChatMessageResponse.builder()
                .id(message.getId())
                .conversationId(conversationId)
                .senderId(message.getSender().getId())
                .senderName(message.getSender().getFullName())
                .senderAvatarUrl(message.getSender().getAvatarUrl())
                .content(message.getContent())
                .clientMessageId(message.getClientMessageId())
                .messageType(message.getMessageType())
                .sharedPersonalRecord(sharedPr)
                .createdAt(message.getCreatedAt())
                .build();
    }

    private SharedPersonalRecordResponse resolveSharedPrFromMessage(Long conversationId, ChatMessage message) {
        if (message.getMessageType() != ChatMessageType.PR_SHARE || message.getContent() == null) {
            return null;
        }
        try {
            ObjectMapper mapper = objectMapper != null
                    ? objectMapper
                    : new ObjectMapper().registerModule(new JavaTimeModule());
            SharedPersonalRecordResponse parsed = mapper.readValue(message.getContent(), SharedPersonalRecordResponse.class);
            if (parsed != null && parsed.getId() != null && sharedPersonalRecordRepository != null && personalRecordService != null) {
                var latestShare = sharedPersonalRecordRepository.findById(parsed.getId());
                if (latestShare.isPresent()) {
                    SharedPersonalRecordResponse liveResponse = personalRecordService.mapToSharedResponse(latestShare.get());
                    liveResponse.setConversationId(conversationId);
                    liveResponse.setChatMessageId(message.getId());
                    return liveResponse;
                }
            }
            if (parsed != null) {
                parsed.setConversationId(conversationId);
                parsed.setChatMessageId(message.getId());
            }
            return parsed;
        } catch (Exception e) {
            log.debug("Could not deserialize PR_SHARE message content for message {}: {}", message.getId(), e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public ChatMessageResponse sendMessage(Long currentUserId, Long conversationId, SendChatMessageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Message payload is required");
        }

        if (request.getConversationId() != null && !request.getConversationId().equals(conversationId)) {
            throw new IllegalArgumentException("Conversation ID does not match the request path");
        }

        String content = request.getContent() == null ? "" : request.getContent().trim();
        if (content.isEmpty()) {
            throw new IllegalArgumentException("Message content is required");
        }
        if (content.length() > 4000) {
            throw new IllegalArgumentException("Message content must be 4000 characters or fewer");
        }

        ChatConversation conversation = conversationRepository.findByIdAndParticipant(conversationId, currentUserId)
                .orElseThrow(ChatConversationNotFoundException::new);

        String clientMessageId = request.getClientMessageId() == null ? null : request.getClientMessageId().trim();
        if (clientMessageId != null && clientMessageId.isEmpty()) {
            clientMessageId = null;
        }

        if (clientMessageId != null) {
            var existingMessage = messageRepository.findByConversationIdAndClientMessageId(conversationId, clientMessageId);
            if (existingMessage.isPresent()) {
                return toResponse(conversation.getId(), existingMessage.get());
            }
        }

        User sender = userRepository.findById(currentUserId)
                .orElseThrow(ChatConversationNotFoundException::new);

        ChatMessageType effectiveType = request.getMessageType() != null ? request.getMessageType() : ChatMessageType.TEXT;

        ChatMessage message = messageRepository.save(ChatMessage.builder()
                .conversation(conversation)
                .sender(sender)
                .content(content)
                .clientMessageId(clientMessageId)
                .messageType(effectiveType)
                .build());

        ZonedDateTime now = message.getCreatedAt();
        conversation.setLastMessageAt(now);
        conversationRepository.save(conversation);

        Long otherUserId = currentUserId.equals(conversation.getCoach().getId())
                ? conversation.getClient().getId()
                : conversation.getCoach().getId();

        ChatMessageResponse response = toResponse(conversation.getId(), message);

        messagingTemplate.convertAndSend("/topic/conversations/" + conversationId, response);
        messagingTemplate.convertAndSendToUser(String.valueOf(otherUserId), "/queue/messages", response);
        return response;
    }

    @Override
    @Transactional
    public void markConversationAsRead(Long currentUserId, Long conversationId) {
        ChatConversation conversation = conversationRepository.findByIdAndParticipant(conversationId, currentUserId)
                .orElseThrow(ChatConversationNotFoundException::new);

        ZonedDateTime now = ZonedDateTime.now();
        if (currentUserId.equals(conversation.getCoach().getId())) {
            conversation.setCoachLastReadAt(now);
        } else if (currentUserId.equals(conversation.getClient().getId())) {
            conversation.setClientLastReadAt(now);
        } else {
            throw new ChatAccessDeniedException();
        }

        conversationRepository.save(conversation);
    }
}
