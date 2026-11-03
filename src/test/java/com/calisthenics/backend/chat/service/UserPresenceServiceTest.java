package com.calisthenics.backend.chat.service;

import com.calisthenics.backend.chat.dto.response.UserPresenceResponse;
import com.calisthenics.backend.chat.presence.UserPresenceRecord;
import com.calisthenics.backend.chat.presence.UserPresenceRepository;
import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import com.calisthenics.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserPresenceServiceTest {

    private ChatConversationRepository conversationRepository;
    private UserPresenceRepository presenceRepository;
    private SimpMessagingTemplate messagingTemplate;
    private UserPresenceService presenceService;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ChatConversationRepository.class);
        presenceRepository = mock(UserPresenceRepository.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        when(conversationRepository.findByCoachIdOrClientIdOrderByLastMessageAtDesc(any()))
                .thenReturn(List.of());
        presenceService = new UserPresenceService(
                conversationRepository,
                presenceRepository,
                messagingTemplate
        );
    }

    @Test
    void keepsUserOnlineUntilLastConnectionDisconnectsAndSupportsReconnect() {
        allowPresenceRecipient(7L, 8L);
        presenceService.connected("phone", 7L);
        presenceService.connected("tablet", 7L);
        presenceService.disconnected("phone");

        verify(messagingTemplate).convertAndSendToUser(
                eq("8"),
                eq("/queue/presence"),
                any(UserPresenceResponse.class)
        );
        verify(presenceRepository).save(any());

        presenceService.disconnected("tablet");
        presenceService.connected("phone-reconnected", 7L);

        verify(messagingTemplate, org.mockito.Mockito.times(3)).convertAndSendToUser(
                eq("8"),
                eq("/queue/presence"),
                any(UserPresenceResponse.class)
        );
        verify(presenceRepository, org.mockito.Mockito.times(3)).save(any());
    }

    @Test
    void heartbeatsAreScopedToTheirAuthenticatedConnectionAndStaleSessionsExpire() {
        Instant now = Instant.now();
        allowPresenceRecipient(12L, 13L);
        presenceService.connected("session", 12L);
        presenceService.heartbeat("session", 99L);

        presenceService.expireStaleSessionsAt(now.plusSeconds(61));
        presenceService.expireStaleSessionsAt(now.plusSeconds(62));

        verify(presenceRepository, org.mockito.Mockito.atLeastOnce()).save(any());
        verify(messagingTemplate, org.mockito.Mockito.times(2)).convertAndSendToUser(
                eq("13"),
                eq("/queue/presence"),
                any(UserPresenceResponse.class)
        );
        verify(presenceRepository, org.mockito.Mockito.times(2)).save(any());
    }

    @Test
    void duplicateDisconnectsDoNotWriteOrPublishAnotherOfflineTransition() {
        allowPresenceRecipient(3L, 4L);
        presenceService.connected("session", 3L);
        presenceService.disconnected("session");
        presenceService.disconnected("session");

        verify(presenceRepository, org.mockito.Mockito.atLeastOnce()).save(any());
        verify(messagingTemplate, org.mockito.Mockito.times(2)).convertAndSendToUser(
                eq("4"),
                eq("/queue/presence"),
                any(UserPresenceResponse.class)
        );
    }

    @Test
    void authenticatedHeartbeatRestoresPresenceAfterAStaleSessionExpires() {
        allowPresenceRecipient(20L, 21L);
        Instant now = Instant.now();
        presenceService.connected("session", 20L);
        presenceService.expireStaleSessionsAt(now.plusSeconds(61));
        presenceService.heartbeat("session", 20L);

        verify(messagingTemplate, org.mockito.Mockito.times(3)).convertAndSendToUser(
                eq("21"),
                eq("/queue/presence"),
                any(UserPresenceResponse.class)
        );
    }

    @Test
    void restartMarksPersistedOnlineUsersOfflineAndAdvancesTheirVersion() {
        UserPresenceRecord previouslyOnline = UserPresenceRecord.builder()
                .userId(31L)
                .status("ONLINE")
                .version(8L)
                .build();
        when(presenceRepository.findAllByStatus("ONLINE"))
                .thenReturn(List.of(previouslyOnline));

        presenceService.expirePersistedOnlineUsersAfterRestart();

        verify(presenceRepository).save(argThat(record ->
                record.getUserId().equals(31L) &&
                        record.getStatus().equals("OFFLINE") &&
                        record.getVersion() == 9L &&
                        record.getLastSeen() != null
        ));
    }

    private void allowPresenceRecipient(Long userId, Long recipientId) {
        User coach = mock(User.class);
        User client = mock(User.class);
        when(coach.getId()).thenReturn(userId);
        when(client.getId()).thenReturn(recipientId);
        ChatConversation conversation = mock(ChatConversation.class);
        when(conversation.getCoach()).thenReturn(coach);
        when(conversation.getClient()).thenReturn(client);
        when(conversationRepository.findByCoachIdOrClientIdOrderByLastMessageAtDesc(eq(userId)))
                .thenReturn(List.of(conversation));
    }
}
