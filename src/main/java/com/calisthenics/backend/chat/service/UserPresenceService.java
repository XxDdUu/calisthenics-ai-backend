package com.calisthenics.backend.chat.service;

import com.calisthenics.backend.chat.dto.response.UserPresenceResponse;
import com.calisthenics.backend.chat.entity.ChatConversation;
import com.calisthenics.backend.chat.presence.UserPresenceRecord;
import com.calisthenics.backend.chat.presence.UserPresenceRepository;
import com.calisthenics.backend.chat.repository.ChatConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.annotation.PostConstruct;
import org.springframework.dao.DataAccessException;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.context.event.EventListener;

import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserPresenceService {

    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofSeconds(60);
    private static final String PRESENCE_DESTINATION = "/queue/presence";

    private final ChatConversationRepository conversationRepository;
    private final UserPresenceRepository presenceRepository;
    private final SimpMessagingTemplate messagingTemplate;

    private final Map<Long, Map<String, Instant>> activeSessions = new HashMap<>();
    private final Map<String, Long> sessionOwners = new HashMap<>();
    private final Map<Long, ZonedDateTime> lastSeenCache = new HashMap<>();
    private final Map<Long, Long> versions = new HashMap<>();

    @PostConstruct
    @Transactional
    public void expirePersistedOnlineUsersAfterRestart() {
        ZonedDateTime now = ZonedDateTime.now(ZoneOffset.UTC);
        for (UserPresenceRecord record : presenceRepository.findAllByStatus("ONLINE")) {
            long version = record.getVersion() + 1L;
            record.setStatus("OFFLINE");
            record.setLastSeen(now);
            record.setVersion(version);
            presenceRepository.save(record);
            versions.put(record.getUserId(), version);
            lastSeenCache.put(record.getUserId(), now);
        }
    }

    @EventListener
    public void handleSessionConnected(SessionConnectedEvent event) {
        String sessionId = StompHeaderAccessor.wrap(event.getMessage()).getSessionId();
        Long userId = principalUserId(event.getUser());
        if (sessionId != null && userId != null) {
            connected(sessionId, userId);
        }
    }

    @EventListener
    public void handleSessionDisconnected(SessionDisconnectEvent event) {
        disconnected(event.getSessionId());
    }

    public synchronized void connected(String sessionId, Long userId) {
        if (sessionId == null || userId == null || sessionOwners.containsKey(sessionId)) {
            return;
        }

        Map<String, Instant> sessions = activeSessions.computeIfAbsent(userId, ignored -> new HashMap<>());
        boolean wasOffline = sessions.isEmpty();
        sessions.put(sessionId, Instant.now());
        sessionOwners.put(sessionId, userId);
        if (wasOffline) {
            ZonedDateTime previousLastSeen = lastSeenFor(userId);
            lastSeenCache.remove(userId);
            long version = nextVersion(userId);
            persistPresence(userId, "ONLINE", previousLastSeen, version);
            publishPresence(userId, "ONLINE", null, version);
        }
    }

    public synchronized void heartbeat(String sessionId, Long userId) {
        if (sessionId == null || userId == null) {
            return;
        }
        Long sessionOwner = sessionOwners.get(sessionId);
        if (sessionOwner != null && !userId.equals(sessionOwner)) {
            return;
        }
        Map<String, Instant> sessions = activeSessions.get(userId);
        if (sessions == null || !sessions.containsKey(sessionId)) {
            connected(sessionId, userId);
        } else {
            sessions.put(sessionId, Instant.now());
        }
    }

    public synchronized void disconnected(String sessionId) {
        Long userId = sessionOwners.remove(sessionId);
        if (userId == null) {
            return;
        }
        Map<String, Instant> sessions = activeSessions.get(userId);
        if (sessions == null) {
            return;
        }
        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            activeSessions.remove(userId);
            markOffline(userId);
        }
    }

    @Scheduled(fixedDelay = 15_000L)
    public synchronized void expireStaleSessions() {
        expireStaleSessionsAt(Instant.now());
    }

    synchronized void expireStaleSessionsAt(Instant now) {
        Instant staleBefore = now.minus(HEARTBEAT_TIMEOUT);
        List<String> staleSessions = new ArrayList<>();
        activeSessions.values().forEach(sessions ->
                sessions.forEach((sessionId, lastHeartbeat) -> {
                    if (lastHeartbeat.isBefore(staleBefore)) {
                        staleSessions.add(sessionId);
                    }
                }));
        staleSessions.forEach(this::disconnected);
    }

    @Transactional(readOnly = true)
    public synchronized List<UserPresenceResponse> getConversationPresence(Long currentUserId) {
        List<ChatConversation> conversations =
                conversationRepository.findByCoachIdOrClientIdOrderByLastMessageAtDesc(currentUserId);
        Set<Long> contactIds = new HashSet<>();
        for (ChatConversation conversation : conversations) {
            Long coachId = conversation.getCoach().getId();
            Long clientId = conversation.getClient().getId();
            contactIds.add(currentUserId.equals(coachId) ? clientId : coachId);
        }

        Map<Long, UserPresenceRecord> persistedPresence = new HashMap<>();
        presenceRepository.findAllById(contactIds).forEach(record ->
                persistedPresence.put(record.getUserId(), record));

        return contactIds.stream()
                .map(userId -> snapshot(userId, persistedPresence.get(userId)))
                .toList();
    }

    private void markOffline(Long userId) {
        ZonedDateTime lastSeen = ZonedDateTime.now(ZoneOffset.UTC);
        lastSeenCache.put(userId, lastSeen);
        long version = nextVersion(userId);
        persistPresence(userId, "OFFLINE", lastSeen, version);
        publishPresence(userId, "OFFLINE", lastSeen, version);
    }

    private void persistPresence(Long userId, String status, ZonedDateTime lastSeen, long version) {
        try {
            presenceRepository.save(UserPresenceRecord.builder()
                    .userId(userId)
                    .lastSeen(lastSeen)
                    .status(status)
                    .version(version)
                    .build());
        } catch (DataAccessException exception) {
            log.error("Could not persist presence for user {}", userId, exception);
        }
    }

    private UserPresenceResponse snapshot(Long userId, UserPresenceRecord persistedPresence) {
        boolean online = activeSessions.containsKey(userId) && !activeSessions.get(userId).isEmpty();
        return UserPresenceResponse.builder()
                .userId(userId)
                .status(online ? "ONLINE" : "OFFLINE")
                .lastSeen(online ? null : lastSeenCache.getOrDefault(
                        userId,
                        persistedPresence == null ? null : persistedPresence.getLastSeen()
                ))
                .version(versions.getOrDefault(
                        userId,
                        persistedPresence == null ? 0L : persistedPresence.getVersion()
                ))
                .build();
    }

    private long nextVersion(Long userId) {
        Long currentVersion = versions.get(userId);
        if (currentVersion == null) {
            try {
                currentVersion = presenceRepository.findById(userId)
                        .map(UserPresenceRecord::getVersion)
                        .orElse(0L);
            } catch (DataAccessException exception) {
                log.error("Could not load presence version for user {}", userId, exception);
                currentVersion = 0L;
            }
        }
        long nextVersion = currentVersion + 1L;
        versions.put(userId, nextVersion);
        return nextVersion;
    }

    private ZonedDateTime lastSeenFor(Long userId) {
        ZonedDateTime cachedLastSeen = lastSeenCache.get(userId);
        if (cachedLastSeen != null) {
            return cachedLastSeen;
        }
        try {
            return presenceRepository.findById(userId)
                    .map(UserPresenceRecord::getLastSeen)
                    .orElse(null);
        } catch (DataAccessException exception) {
            log.error("Could not load last-seen timestamp for user {}", userId, exception);
            return null;
        }
    }

    private void publishPresence(Long userId, String status, ZonedDateTime lastSeen, long version) {
        UserPresenceResponse update = UserPresenceResponse.builder()
                .userId(userId)
                .status(status)
                .lastSeen(lastSeen)
                .version(version)
                .build();
        for (Long recipientId : authorizedRecipients(userId)) {
            try {
                messagingTemplate.convertAndSendToUser(
                        recipientId.toString(),
                        PRESENCE_DESTINATION,
                        update
                );
            } catch (MessagingException exception) {
                log.warn("Could not publish presence for user {} to chat participant {}",
                        userId, recipientId, exception);
            }
        }
    }

    private Set<Long> authorizedRecipients(Long userId) {
        Set<Long> recipients = new HashSet<>();
        for (ChatConversation conversation :
                conversationRepository.findByCoachIdOrClientIdOrderByLastMessageAtDesc(userId)) {
            Long coachId = conversation.getCoach().getId();
            Long clientId = conversation.getClient().getId();
            recipients.add(userId.equals(coachId) ? clientId : coachId);
        }
        return recipients;
    }

    private Long principalUserId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            return null;
        }
        try {
            return Long.valueOf(principal.getName());
        } catch (NumberFormatException exception) {
            log.warn("Ignoring presence connection with a non-numeric principal");
            return null;
        }
    }
}
