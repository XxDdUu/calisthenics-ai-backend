package com.calisthenics.backend.aichat;

import com.calisthenics.backend.aichat.dto.CreateAiConversationRequest;
import com.calisthenics.backend.aichat.dto.SendAiMessageRequest;
import com.calisthenics.backend.aichat.entity.AiConversation;
import com.calisthenics.backend.aichat.repository.AiConversationRepository;
import com.calisthenics.backend.aichat.repository.AiMessageRepository;
import com.calisthenics.backend.security.JwtTokenProvider;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class AiAssistantIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiConversationRepository aiConversationRepository;

    @Autowired
    private AiMessageRepository aiMessageRepository;

    @Autowired
    private JwtTokenProvider tokenProvider;

    private User user1;
    private User user2;
    private String token1;
    private String token2;

    @BeforeEach
    void setUp() {
        long ts = System.currentTimeMillis();
        user1 = userRepository.save(User.builder()
                .email("ai_user1_" + ts + "@test.com")
                .fullName("AI User 1")
                .role(Role.ROLE_USER)
                .build());

        user2 = userRepository.save(User.builder()
                .email("ai_user2_" + ts + "@test.com")
                .fullName("AI User 2")
                .role(Role.ROLE_USER)
                .build());

        token1 = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(user1));
        token2 = tokenProvider.generateAccessTokenFromUserPrincipal(UserPrincipal.create(user2));
    }

    @Test
    @DisplayName("Complete AI Chat flow: create conversation, send message, retrieve history, delete, and verify isolation")
    void testCompleteAiChatWorkflow() throws Exception {
        // 1. Create a new conversation for User 1
        CreateAiConversationRequest createReq = new CreateAiConversationRequest("Planche Journey");
        String createRes = mockMvc.perform(post("/api/v1/ai/conversations")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Planche Journey"))
                .andReturn().getResponse().getContentAsString();

        Long conversationId = objectMapper.readTree(createRes).get("id").asLong();

        // 2. User 2 should NOT see User 1's conversation
        mockMvc.perform(get("/api/v1/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());

        // 3. Send message from User 1
        SendAiMessageRequest msgReq = new SendAiMessageRequest("Give me coaching advice on planche lean form", null);
        mockMvc.perform(post("/api/v1/ai/conversations/" + conversationId + "/messages")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.content").isNotEmpty())
                .andExpect(jsonPath("$.conversationId").value(conversationId));

        // 4. Retrieve conversation with message history
        mockMvc.perform(get("/api/v1/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.messages.length()").value(2)) // user msg + assistant msg
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[1].role").value("assistant"));

        // 5. Get latest conversation summary
        mockMvc.perform(get("/api/v1/ai/conversations/latest")
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conversationId))
                .andExpect(jsonPath("$.title").value("Planche Journey"))
                .andExpect(jsonPath("$.lastMessagePreview").isNotEmpty());

        // 6. User 2 cannot delete User 1's conversation
        mockMvc.perform(delete("/api/v1/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());

        // 7. User 1 deletes their conversation
        mockMvc.perform(delete("/api/v1/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNoContent());

        // 8. Verify it is deleted
        mockMvc.perform(get("/api/v1/ai/conversations/" + conversationId)
                        .header("Authorization", "Bearer " + token1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Quick message auto-creates conversation if none exists")
    void testQuickMessage_AutoCreatesConversation() throws Exception {
        SendAiMessageRequest msgReq = new SendAiMessageRequest("Hello AI Coach!", null);
        mockMvc.perform(post("/api/v1/ai/conversations/messages")
                        .header("Authorization", "Bearer " + token1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(msgReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.conversationId").exists());

        // Verify conversation was created in db
        assertThat(aiConversationRepository.findByUserIdOrderByUpdatedAtDesc(user1.getId())).hasSize(1);
    }
}
