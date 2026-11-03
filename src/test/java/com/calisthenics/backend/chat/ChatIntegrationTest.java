package com.calisthenics.backend.chat;

import com.calisthenics.backend.chat.dto.request.CreateChatConversationRequest;
import com.calisthenics.backend.chat.dto.request.SendChatMessageRequest;
import com.calisthenics.backend.chat.dto.response.ChatConversationResponse;
import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.chat.repository.ChatMessageRepository;
import com.calisthenics.backend.chat.service.ChatMessageService;
import com.calisthenics.backend.chat.service.ChatService;
import com.calisthenics.backend.chat.service.UserPresenceService;
import com.calisthenics.backend.coachclient.entity.CoachClientRelationship;
import com.calisthenics.backend.coachclient.repository.CoachClientRelationshipRepository;
import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChatIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CoachClientRelationshipRepository relationshipRepository;

    @Autowired
    private ChatConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository messageRepository;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ChatMessageService chatMessageService;

    @Autowired
    private UserPresenceService userPresenceService;

    @Test
    void userCanCreateConversationAndSendMessagesWithinActiveRelationship() throws Exception {
        User coach = createUser();
        User client = createUser();
        createRelationship(coach, client);

        String conversationResponse = mockMvc.perform(post("/api/v1/chat/conversations")
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"otherUserId\":" + client.getId() + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.otherUserId").value(client.getId()))
                .andReturn().getResponse().getContentAsString();

        Long conversationId = objectMapper.readTree(conversationResponse).get("id").asLong();

        mockMvc.perform(get("/api/v1/chat/conversations").with(authenticatedAs(client)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(conversationId));

        String messageResponse = mockMvc.perform(post("/api/v1/chat/conversations/{conversationId}/messages", conversationId)
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Hello coach!\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Hello coach!"))
                .andReturn().getResponse().getContentAsString();

        Long messageId = objectMapper.readTree(messageResponse).get("id").asLong();
        assertThat(messageRepository.findById(messageId)).isPresent();

        mockMvc.perform(post("/api/v1/chat/conversations/{conversationId}/read", conversationId)
                        .with(authenticatedAs(client)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/chat/unread-count").with(authenticatedAs(coach)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void participantCannotAccessConversationOutsideActiveRelationship() {
        User coach = createUser();
        User client = createUser();
        User outsider = createUser();
        createRelationship(coach, client);

        ChatConversationResponse conversation = chatService.getOrCreateConversation(coach.getId(), client.getId());
        assertThatThrownBy(() -> chatMessageService.listMessages(outsider.getId(), conversation.getId(), 0, 20))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void rejectsEmptyOrBlankMessage() throws Exception {
        User coach = createUser();
        User client = createUser();
        createRelationship(coach, client);

        ChatConversationResponse conversation = chatService.getOrCreateConversation(coach.getId(), client.getId());

        mockMvc.perform(post("/api/v1/chat/conversations/{conversationId}/messages", conversation.getId())
                        .with(authenticatedAs(coach))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void presenceSnapshotRequiresAuthenticationAndOnlyIncludesChatParticipants() throws Exception {
        User coach = createUser();
        User client = createUser();
        User stranger = createUser();
        createRelationship(coach, client);
        chatService.getOrCreateConversation(coach.getId(), client.getId());

        String clientSession = "presence-client-" + client.getId();
        String strangerSession = "presence-stranger-" + stranger.getId();
        userPresenceService.connected(clientSession, client.getId());
        userPresenceService.connected(strangerSession, stranger.getId());
        try {
            mockMvc.perform(get("/api/v1/chat/presence"))
                    .andExpect(status().isUnauthorized());

            mockMvc.perform(get("/api/v1/chat/presence").with(authenticatedAs(coach)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].userId").value(client.getId()))
                    .andExpect(jsonPath("$[0].status").value("ONLINE"));
        } finally {
            userPresenceService.disconnected(clientSession);
            userPresenceService.disconnected(strangerSession);
        }
    }

    @Test
    void messagesPaginationAndOrdering() throws Exception {
        User coach = createUser();
        User client = createUser();
        createRelationship(coach, client);

        ChatConversationResponse conversation = chatService.getOrCreateConversation(coach.getId(), client.getId());

        // Send 3 messages
        for (int i = 1; i <= 3; i++) {
            SendChatMessageRequest request = new SendChatMessageRequest();
            request.setContent("Message " + i);
            chatMessageService.sendMessage(coach.getId(), conversation.getId(), request);
        }

        // Test ASC ordering (oldest first)
        mockMvc.perform(get("/api/v1/chat/conversations/{conversationId}/messages", conversation.getId())
                        .with(authenticatedAs(client))
                        .param("direction", "asc")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Message 1"))
                .andExpect(jsonPath("$[1].content").value("Message 2"));

        // Test DESC ordering (newest first)
        mockMvc.perform(get("/api/v1/chat/conversations/{conversationId}/messages", conversation.getId())
                        .with(authenticatedAs(client))
                        .param("direction", "desc")
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].content").value("Message 3"))
                .andExpect(jsonPath("$[1].content").value("Message 2"));
    }

    @Test
    void unauthorizedUserCannotAccessConversationMessages() throws Exception {
        User coach = createUser();
        User client = createUser();
        User outsider = createUser();
        createRelationship(coach, client);

        ChatConversationResponse conversation = chatService.getOrCreateConversation(coach.getId(), client.getId());

        mockMvc.perform(get("/api/v1/chat/conversations/{conversationId}/messages", conversation.getId())
                        .with(authenticatedAs(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    void idempotentMessageSendingReturnsExistingMessageWithoutDuplicates() throws Exception {
        User coach = createUser();
        User client = createUser();
        createRelationship(coach, client);
        ChatConversationResponse conversation = chatService.getOrCreateConversation(coach.getId(), client.getId());

        String clientMessageId = UUID.randomUUID().toString();
        String payload = String.format("{\"content\":\"Hello\",\"clientMessageId\":\"%s\"}", clientMessageId);

        String firstResponse = mockMvc.perform(post("/api/v1/chat/conversations/{conversationId}/messages", conversation.getId())
                        .with(authenticatedAs(coach))
                        .header("X-Correlation-ID", "corr-test-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.clientMessageId").value(clientMessageId))
                .andReturn().getResponse().getContentAsString();

        Long firstMessageId = objectMapper.readTree(firstResponse).get("id").asLong();

        // Send duplicate request with the exact same clientMessageId
        String secondResponse = mockMvc.perform(post("/api/v1/chat/conversations/{conversationId}/messages", conversation.getId())
                        .with(authenticatedAs(coach))
                        .header("X-Correlation-ID", "corr-test-124")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstMessageId))
                .andExpect(jsonPath("$.clientMessageId").value(clientMessageId))
                .andReturn().getResponse().getContentAsString();

        Long secondMessageId = objectMapper.readTree(secondResponse).get("id").asLong();
        assertThat(firstMessageId).isEqualTo(secondMessageId);

        // Verify only 1 message exists in DB for this conversation
        long count = messageRepository.findByConversationIdOrderByCreatedAtAsc(conversation.getId(), org.springframework.data.domain.Pageable.unpaged()).size();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void correlationIdHeaderIsReturnedInResponse() throws Exception {
        User coach = createUser();
        User client = createUser();
        createRelationship(coach, client);
        ChatConversationResponse conversation = chatService.getOrCreateConversation(coach.getId(), client.getId());

        String correlationId = "corr-" + UUID.randomUUID();

        mockMvc.perform(get("/api/v1/chat/conversations/{conversationId}/messages", conversation.getId())
                        .with(authenticatedAs(coach))
                        .header("X-Correlation-ID", correlationId))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("X-Correlation-ID", correlationId));
    }

    private CoachClientRelationship createRelationship(User coach, User client) {
        CoachClientRelationship relationship = CoachClientRelationship.builder()
                .coach(coach)
                .client(client)
                .requestedBy(coach)
                .status(CoachClientRelationshipStatus.ACTIVE)
                .version(0L)
                .build();
        return relationshipRepository.saveAndFlush(relationship);
    }

    private User createUser() {
        String email = UUID.randomUUID() + "@example.com";
        return userRepository.saveAndFlush(User.builder()
                .email(email)
                .fullName("Test User")
                .role(Role.ROLE_USER)
                .build());
    }

    private RequestPostProcessor authenticatedAs(User user) {
        return user(UserPrincipal.create(user));
    }
}
