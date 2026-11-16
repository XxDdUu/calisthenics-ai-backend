package com.calisthenics.backend.notification.service;

import com.calisthenics.backend.notification.dto.CreateAnnouncementRequest;
import com.calisthenics.backend.notification.dto.NotificationResponse;
import com.calisthenics.backend.notification.model.Notification;
import com.calisthenics.backend.notification.model.NotificationType;
import com.calisthenics.backend.notification.repository.NotificationRepository;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final ClientProfileRepository clientProfileRepository;

    @Transactional
    public NotificationResponse createNotification(
            User recipient,
            User sender,
            NotificationType type,
            Long referenceId,
            String title,
            String message) {
        return createNotification(
                recipient,
                sender,
                type,
                referenceId,
                null,
                referenceId,
                null,
                title,
                message,
                null
        );
    }

    @Transactional
    public NotificationResponse createNotification(
            User recipient,
            User sender,
            NotificationType type,
            Long referenceId,
            String relatedEntityType,
            Long relatedEntityId,
            String deepLink,
            String title,
            String message,
            String eventKey) {

        if (recipient == null) {
            log.warn("Cannot create notification with null recipient");
            return null;
        }

        String normalizedEventKey = (eventKey != null && !eventKey.isBlank()) ? eventKey.trim() : null;
        if (normalizedEventKey != null
                && recipient.getId() != null
                && notificationRepository.existsByRecipientIdAndEventKey(recipient.getId(), normalizedEventKey)) {
            log.debug("Skipping duplicate notification for recipient {} eventKey={}", recipient.getId(), normalizedEventKey);
            return null;
        }

        Long resolvedEntityId = relatedEntityId != null ? relatedEntityId : referenceId;
        Long resolvedReferenceId = referenceId != null ? referenceId : relatedEntityId;

        Notification notification = Notification.builder()
                .recipient(recipient)
                .sender(sender)
                .notificationType(type)
                .referenceId(resolvedReferenceId)
                .relatedEntityType(relatedEntityType)
                .relatedEntityId(resolvedEntityId)
                .deepLink(deepLink)
                .eventKey(normalizedEventKey)
                .title(title)
                .message(message)
                .isRead(false)
                .build();

        Notification saved = notificationRepository.save(notification);
        log.info("Created notification ID {} ({}) for user {}", saved.getId(), type, recipient.getId());
        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId, Boolean unreadOnly) {
        return getNotifications(userId, unreadOnly, null, null);
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> getNotifications(Long userId, Boolean unreadOnly, Integer page, Integer size) {
        if (page != null || size != null) {
            int resolvedPage = page != null ? Math.max(0, page) : 0;
            int resolvedSize = size != null ? Math.min(Math.max(1, size), MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
            return getNotificationsPage(userId, unreadOnly, resolvedPage, resolvedSize).getContent();
        }
        List<Notification> list = (Boolean.TRUE.equals(unreadOnly))
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);

        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public Page<NotificationResponse> getNotificationsPage(Long userId, Boolean unreadOnly, int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<Notification> result = Boolean.TRUE.equals(unreadOnly)
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId, pageable)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId, pageable);
        return result.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public NotificationResponse getNotification(Long userId, Long notificationId) {
        Notification notification = findOwnedNotificationOrThrow(userId, notificationId);
        return mapToResponse(notification);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = findOwnedNotificationOrThrow(userId, notificationId);

        if (!notification.isRead()) {
            notification.setRead(true);
            notification.setReadAt(ZonedDateTime.now());
            notification = notificationRepository.save(notification);
        }

        return mapToResponse(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    @Transactional
    public int publishAdminAnnouncement(Long adminUserId, CreateAnnouncementRequest request) {
        if (request == null || request.getTitle() == null || request.getTitle().isBlank()
                || request.getMessage() == null || request.getMessage().isBlank()) {
            throw new IllegalArgumentException("Announcement title and message are required");
        }
        User adminSender = adminUserId != null ? userRepository.findById(adminUserId).orElse(null) : null;
        String cleanTitle = request.getTitle().trim();
        String cleanMessage = request.getMessage().trim();
        String targetRole = request.getTargetRole() != null ? request.getTargetRole().trim().toUpperCase(Locale.US) : "ROLE_USER";

        List<User> recipients = resolveAnnouncementRecipients(request.getRecipientUserIds(), targetRole, adminUserId);
        String baseKey = (request.getEventKey() != null && !request.getEventKey().isBlank())
                ? "ADMIN_ANNOUNCEMENT:" + request.getEventKey().trim()
                : "ADMIN_ANNOUNCEMENT:" + Objects.hash(cleanTitle, cleanMessage, targetRole);
        String deepLink = (request.getDeepLink() != null && !request.getDeepLink().isBlank())
                ? request.getDeepLink().trim()
                : "notifications";

        int createdCount = 0;
        for (User recipient : recipients) {
            NotificationResponse created = createNotification(
                    recipient,
                    adminSender,
                    NotificationType.ADMIN_ANNOUNCEMENT,
                    null,
                    "ANNOUNCEMENT",
                    null,
                    deepLink,
                    cleanTitle,
                    cleanMessage,
                    baseKey
            );
            if (created != null) {
                createdCount++;
            }
        }
        log.info("Published admin announcement '{}' to {} recipients (targetRole={})", cleanTitle, createdCount, targetRole);
        return createdCount;
    }

    private List<User> resolveAnnouncementRecipients(List<Long> explicitIds, String targetRole, Long adminUserId) {
        if (explicitIds != null && !explicitIds.isEmpty()) {
            return userRepository.findAllById(explicitIds);
        }
        List<User> allUsers = userRepository.findAll();
        if ("ALL".equals(targetRole)) {
            return allUsers.stream()
                    .filter(u -> adminUserId == null || !u.getId().equals(adminUserId))
                    .toList();
        }
        if ("ROLE_ADMIN".equals(targetRole) || "ADMIN".equals(targetRole)) {
            return allUsers.stream()
                    .filter(u -> u.getRole() == Role.ROLE_ADMIN)
                    .toList();
        }
        List<User> regularUsers = allUsers.stream()
                .filter(u -> u.getRole() == Role.ROLE_USER)
                .toList();
        if ("COACH".equals(targetRole) && coachProfileRepository != null && !regularUsers.isEmpty()) {
            Set<Long> coachIds = coachProfileRepository.findUserIdsWithProfiles(
                    regularUsers.stream().map(User::getId).toList());
            return regularUsers.stream().filter(u -> coachIds.contains(u.getId())).toList();
        }
        if ("CLIENT".equals(targetRole) && clientProfileRepository != null && !regularUsers.isEmpty()) {
            Set<Long> clientIds = clientProfileRepository.findUserIdsWithProfiles(
                    regularUsers.stream().map(User::getId).toList());
            return regularUsers.stream().filter(u -> clientIds.contains(u.getId())).toList();
        }
        return regularUsers;
    }

    private Notification findOwnedNotificationOrThrow(Long userId, Long notificationId) {
        return notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> {
                    if (notificationRepository.existsById(notificationId)) {
                        return new AccessDeniedException("You do not have permission to access this notification");
                    }
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found");
                });
    }

    public NotificationResponse mapToResponse(Notification n) {
        Long recipientId = n.getRecipient() != null ? n.getRecipient().getId() : null;
        Long resolvedEntityId = n.getRelatedEntityId() != null ? n.getRelatedEntityId() : n.getReferenceId();
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(recipientId)
                .recipientUserId(recipientId)
                .senderId(n.getSender() != null ? n.getSender().getId() : null)
                .senderName(n.getSender() != null ? n.getSender().getFullName() : null)
                .senderAvatarUrl(n.getSender() != null ? n.getSender().getAvatarUrl() : null)
                .type(n.getNotificationType())
                .notificationType(n.getNotificationType())
                .referenceId(n.getReferenceId() != null ? n.getReferenceId() : resolvedEntityId)
                .relatedEntityType(n.getRelatedEntityType())
                .relatedEntityId(resolvedEntityId)
                .deepLink(n.getDeepLink())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .readAt(n.getReadAt())
                .build();
    }
}

