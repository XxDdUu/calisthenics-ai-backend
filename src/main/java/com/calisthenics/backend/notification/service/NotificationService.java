package com.calisthenics.backend.notification.service;

import com.calisthenics.backend.notification.dto.NotificationResponse;
import com.calisthenics.backend.notification.model.Notification;
import com.calisthenics.backend.notification.model.NotificationType;
import com.calisthenics.backend.notification.repository.NotificationRepository;
import com.calisthenics.backend.user.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Transactional
    public NotificationResponse createNotification(
            User recipient,
            User sender,
            NotificationType type,
            Long referenceId,
            String title,
            String message) {

        if (recipient == null) {
            log.warn("Cannot create notification with null recipient");
            return null;
        }

        Notification notification = Notification.builder()
                .recipient(recipient)
                .sender(sender)
                .notificationType(type)
                .referenceId(referenceId)
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
        List<Notification> list = (Boolean.TRUE.equals(unreadOnly))
                ? notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(userId)
                : notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);

        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndIsReadFalse(userId);
    }

    @Transactional
    public NotificationResponse markAsRead(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndRecipientId(notificationId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found"));

        if (!notification.isRead()) {
            notification.setRead(true);
            notification = notificationRepository.save(notification);
        }

        return mapToResponse(notification);
    }

    @Transactional
    public void markAllAsRead(Long userId) {
        notificationRepository.markAllAsReadByRecipientId(userId);
    }

    public NotificationResponse mapToResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipient() != null ? n.getRecipient().getId() : null)
                .senderId(n.getSender() != null ? n.getSender().getId() : null)
                .senderName(n.getSender() != null ? n.getSender().getFullName() : null)
                .senderAvatarUrl(n.getSender() != null ? n.getSender().getAvatarUrl() : null)
                .notificationType(n.getNotificationType())
                .referenceId(n.getReferenceId())
                .title(n.getTitle())
                .message(n.getMessage())
                .isRead(n.isRead())
                .createdAt(n.getCreatedAt())
                .build();
    }
}

