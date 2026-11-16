package com.calisthenics.backend.notification.dto;

import com.calisthenics.backend.notification.model.NotificationType;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private Long recipientUserId;
    private Long senderId;
    private String senderName;
    private String senderAvatarUrl;
    private NotificationType type;
    private NotificationType notificationType;
    private Long referenceId;
    private String relatedEntityType;
    private Long relatedEntityId;
    private String deepLink;
    private String title;
    private String message;
    @JsonProperty("isRead")
    private boolean isRead;
    private ZonedDateTime createdAt;
    private ZonedDateTime readAt;
}

