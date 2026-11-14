package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.dto.AdminConversationDetailResponse;
import com.calisthenics.backend.adminai.dto.AdminConversationSummaryResponse;
import com.calisthenics.backend.adminai.dto.AdminMessageResponse;
import com.calisthenics.backend.adminai.dto.CreateAdminConversationRequest;
import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import com.calisthenics.backend.adminai.entity.AdminAiMessage;
import com.calisthenics.backend.adminai.repository.AdminAiConversationRepository;
import com.calisthenics.backend.adminai.repository.AdminAiMessageRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminConversationService {

    public static final String DEFAULT_CONVERSATION_TITLE = "New Admin Session";

    private final AdminAiConversationRepository conversationRepository;
    private final AdminAiMessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<AdminConversationSummaryResponse> getConversations(Long adminUserId) {
        requireAdminUser(adminUserId);
        return conversationRepository.findByAdminUserIdOrderByUpdatedAtDesc(adminUserId).stream()
                .map(this::mapToSummary)
                .toList();
    }

    @Transactional
    public AdminConversationSummaryResponse createConversation(Long adminUserId, CreateAdminConversationRequest request) {
        User admin = requireAdminUser(adminUserId);
        String title = (request != null && request.getTitle() != null && !request.getTitle().isBlank())
                ? request.getTitle().trim()
                : DEFAULT_CONVERSATION_TITLE;

        AdminAiConversation conversation = AdminAiConversation.builder()
                .adminUser(admin)
                .title(title)
                .build();
        AdminAiConversation saved = conversationRepository.save(conversation);
        log.info("Created Admin AI conversation id={} for adminId={}", saved.getId(), adminUserId);
        return mapToSummary(saved);
    }

    @Transactional(readOnly = true)
    public AdminConversationDetailResponse getConversationDetail(Long adminUserId, Long conversationId) {
        requireAdminUser(adminUserId);
        AdminAiConversation conversation = conversationRepository.findByIdAndAdminUserId(conversationId, adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin AI conversation not found"));

        List<AdminMessageResponse> messages = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId).stream()
                .map(this::mapToMessageResponse)
                .toList();

        return AdminConversationDetailResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .messages(messages)
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public List<AdminMessageResponse> getConversationMessages(Long adminUserId, Long conversationId) {
        return getConversationDetail(adminUserId, conversationId).getMessages();
    }

    @Transactional
    public void deleteConversation(Long adminUserId, Long conversationId) {
        requireAdminUser(adminUserId);
        AdminAiConversation conversation = conversationRepository.findByIdAndAdminUserId(conversationId, adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin AI conversation not found"));
        conversationRepository.delete(conversation);
        log.info("Deleted Admin AI conversation id={} for adminId={}", conversationId, adminUserId);
    }

    @Transactional
    public AdminAiConversation resolveOrCreateConversation(Long adminUserId, Long conversationId) {
        User admin = requireAdminUser(adminUserId);
        if (conversationId != null && conversationId > 0) {
            return conversationRepository.findByIdAndAdminUserId(conversationId, adminUserId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin AI conversation not found"));
        }
        return conversationRepository.findFirstByAdminUserIdOrderByUpdatedAtDesc(adminUserId)
                .orElseGet(() -> conversationRepository.save(AdminAiConversation.builder()
                        .adminUser(admin)
                        .title(DEFAULT_CONVERSATION_TITLE)
                        .build()));
    }

    @Transactional(readOnly = true)
    public Optional<AdminAiConversation> findOwnedConversation(Long adminUserId, Long conversationId) {
        if (adminUserId == null || conversationId == null) {
            return Optional.empty();
        }
        return conversationRepository.findByIdAndAdminUserId(conversationId, adminUserId);
    }

    @Transactional
    public AdminAiMessage saveMessage(
            AdminAiConversation conversation,
            String role,
            String content,
            String intent,
            String metadataJson
    ) {
        AdminAiMessage msg = AdminAiMessage.builder()
                .conversation(conversation)
                .role(role)
                .content(content)
                .intent(intent)
                .metadata(metadataJson)
                .build();
        AdminAiMessage saved = messageRepository.save(msg);
        conversation.setUpdatedAt(ZonedDateTime.now());
        conversationRepository.save(conversation);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AdminAiMessage> getRecentHistoryForPrompt(Long conversationId) {
        return messageRepository.findTop20ByConversationIdOrderByCreatedAtDesc(conversationId);
    }

    @Transactional
    public void updateTitleIfDefault(AdminAiConversation conversation, String newTitle) {
        if (DEFAULT_CONVERSATION_TITLE.equalsIgnoreCase(conversation.getTitle())
                && newTitle != null && !newTitle.isBlank()) {
            conversation.setTitle(newTitle.trim());
            conversation.setUpdatedAt(ZonedDateTime.now());
            conversationRepository.save(conversation);
        }
    }

    public User requireAdminUser(Long adminUserId) {
        if (adminUserId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Administrator authentication required");
        }
        User user = userRepository.findById(adminUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Administrator account not found"));
        if (user.getRole() != Role.ROLE_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied: ADMIN role required");
        }
        return user;
    }

    public AdminConversationSummaryResponse mapToSummary(AdminAiConversation conversation) {
        Optional<AdminAiMessage> lastMessage =
                messageRepository.findTopByConversationIdOrderByCreatedAtDesc(conversation.getId());
        String preview = lastMessage.map(AdminAiMessage::getContent).orElse("No messages yet");
        if (preview.length() > 90) {
            preview = preview.substring(0, 87) + "...";
        }
        String lastRole = lastMessage.map(AdminAiMessage::getRole).orElse(null);
        ZonedDateTime lastInteraction = lastMessage.map(AdminAiMessage::getCreatedAt).orElse(conversation.getUpdatedAt());
        int count = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId()).size();

        return AdminConversationSummaryResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .lastMessagePreview(preview)
                .lastMessageRole(lastRole)
                .lastInteractionAt(lastInteraction)
                .createdAt(conversation.getCreatedAt())
                .messageCount(count)
                .build();
    }

    public AdminMessageResponse mapToMessageResponse(AdminAiMessage message) {
        return AdminMessageResponse.builder()
                .id(message.getId())
                .conversationId(message.getConversation().getId())
                .role(message.getRole())
                .content(message.getContent())
                .intent(message.getIntent())
                .metadata(message.getMetadata())
                .createdAt(message.getCreatedAt())
                .build();
    }
}
