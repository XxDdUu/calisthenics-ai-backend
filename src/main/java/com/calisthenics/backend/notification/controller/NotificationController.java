package com.calisthenics.backend.notification.controller;

import com.calisthenics.backend.notification.dto.CreateAnnouncementRequest;
import com.calisthenics.backend.notification.dto.NotificationResponse;
import com.calisthenics.backend.notification.service.NotificationService;
import com.calisthenics.backend.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Endpoints for managing user notifications")
@SecurityRequirement(name = "bearerAuth")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @Operation(summary = "Get notifications for authenticated user (optionally paginated)")
    public ResponseEntity<List<NotificationResponse>> getNotifications(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(notificationService.getNotifications(currentUser.getId(), unreadOnly, page, size));
    }

    @GetMapping("/paged")
    @Operation(summary = "Get paginated notifications page metadata for authenticated user")
    public ResponseEntity<Page<NotificationResponse>> getNotificationsPaged(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(notificationService.getNotificationsPage(currentUser.getId(), unreadOnly, page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single notification belonging to the authenticated user")
    public ResponseEntity<NotificationResponse> getNotification(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.getNotification(currentUser.getId(), id));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Get unread notifications count for authenticated user")
    public ResponseEntity<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        long count = notificationService.getUnreadCount(currentUser.getId());
        return ResponseEntity.ok(Collections.singletonMap("unreadCount", count));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a notification as read")
    public ResponseEntity<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        return ResponseEntity.ok(notificationService.markAsRead(currentUser.getId(), id));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read for authenticated user")
    public ResponseEntity<Map<String, String>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        notificationService.markAllAsRead(currentUser.getId());
        return ResponseEntity.ok(Collections.singletonMap("message", "All notifications marked as read"));
    }

    @PostMapping("/announcements")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create and broadcast a platform announcement to targeted users (Admin only)")
    public ResponseEntity<Map<String, Object>> createAnnouncement(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @Valid @RequestBody CreateAnnouncementRequest request) {
        int recipientCount = notificationService.publishAdminAnnouncement(currentUser.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "status", "created",
                "recipientCount", recipientCount
        ));
    }
}

