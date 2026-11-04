package com.calisthenics.backend.notification.dto;

import com.calisthenics.backend.notification.model.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private Long recipientId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private NotificationType notificationType;
    private Long referenceId;
    private String title;
    private String message;
    private boolean isRead;
    private ZonedDateTime createdAt;
}

