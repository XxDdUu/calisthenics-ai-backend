package com.calisthenics.backend.notification;

import com.calisthenics.backend.notification.dto.NotificationResponse;
import com.calisthenics.backend.notification.model.Notification;
import com.calisthenics.backend.notification.model.NotificationType;
import com.calisthenics.backend.notification.repository.NotificationRepository;
import com.calisthenics.backend.notification.service.NotificationService;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User recipient;
    private User sender;

    @BeforeEach
    void setUp() {
        recipient = User.builder().id(10L).email("coach@test.com").fullName("Coach Test").role(Role.ROLE_USER).build();
        sender = User.builder().id(20L).email("client@test.com").fullName("Client Test").role(Role.ROLE_USER).build();
    }

    @Test
    void createNotification_Success() {
        when(notificationRepository.save(any())).thenAnswer(invocation -> {
            Notification n = invocation.getArgument(0);
            n.setId(100L);
            n.setCreatedAt(ZonedDateTime.now());
            return n;
        });

        NotificationResponse response = notificationService.createNotification(
                recipient, sender, NotificationType.PR_SHARED, 50L, "New PR", "Client shared a PR"
        );

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(100L);
        assertThat(response.getRecipientId()).isEqualTo(10L);
        assertThat(response.getSenderId()).isEqualTo(20L);
        assertThat(response.getNotificationType()).isEqualTo(NotificationType.PR_SHARED);
        assertThat(response.isRead()).isFalse();
    }

    @Test
    void getNotifications_UnreadOnly() {
        Notification n = Notification.builder()
                .id(101L).recipient(recipient).sender(sender)
                .notificationType(NotificationType.COACH_FEEDBACK)
                .title("Feedback").message("Good job").isRead(false)
                .createdAt(ZonedDateTime.now())
                .build();

        when(notificationRepository.findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(10L))
                .thenReturn(List.of(n));

        List<NotificationResponse> list = notificationService.getNotifications(10L, true);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getTitle()).isEqualTo("Feedback");
    }

    @Test
    void markAsRead_Success() {
        Notification n = Notification.builder()
                .id(102L).recipient(recipient).sender(sender)
                .notificationType(NotificationType.PR_SHARED)
                .isRead(false)
                .build();

        when(notificationRepository.findByIdAndRecipientId(102L, 10L)).thenReturn(Optional.of(n));
        when(notificationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.markAsRead(10L, 102L);

        assertThat(response.isRead()).isTrue();
    }

    @Test
    void markAsRead_NotFound_ThrowsException() {
        when(notificationRepository.findByIdAndRecipientId(999L, 10L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> notificationService.markAsRead(10L, 999L));
    }
}

