package com.calisthenics.backend.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnnouncementRequest {

    @NotBlank(message = "Announcement title is required")
    @Size(max = 255, message = "Announcement title must not exceed 255 characters")
    private String title;

    @NotBlank(message = "Announcement message is required")
    @Size(max = 2000, message = "Announcement message must not exceed 2000 characters")
    private String message;

    /**
     * Target role or audience group: ALL, ROLE_USER, COACH, CLIENT, ROLE_ADMIN (defaults to ROLE_USER).
     */
    private String targetRole;

    /**
     * Optional explicit recipient user IDs. When non-empty, overrides targetRole filtering.
     */
    private List<Long> recipientUserIds;

    @Size(max = 255)
    private String deepLink;

    @Size(max = 120)
    private String eventKey;
}
