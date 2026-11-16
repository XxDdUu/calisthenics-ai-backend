package com.calisthenics.backend.notification;

import com.calisthenics.backend.notification.dto.CreateAnnouncementRequest;
import com.calisthenics.backend.notification.dto.NotificationResponse;
import com.calisthenics.backend.notification.model.Notification;
import com.calisthenics.backend.notification.model.NotificationType;
import com.calisthenics.backend.notification.repository.NotificationRepository;
import com.calisthenics.backend.notification.service.NotificationService;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceExtendedTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CoachProfileRepository coachProfileRepository;

    @Mock
    private ClientProfileRepository clientProfileRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User admin;
    private User coach;
    private User client;

    @BeforeEach
    void setUp() {
        admin = User.builder().id(1L).email("admin@test.com").fullName("Admin").role(Role.ROLE_ADMIN).build();
        coach = User.builder().id(2L).email("coach@test.com").fullName("Coach").role(Role.ROLE_USER).build();
        client = User.builder().id(3L).email("client@test.com").fullName("Client").role(Role.ROLE_USER).build();
    }

    @Test
    void createNotification_skipsDuplicateWhenEventKeyAlreadyExists() {
        when(notificationRepository.existsByRecipientIdAndEventKey(3L, "WORKOUT_ASSIGNED:10:20")).thenReturn(true);

        NotificationResponse response = notificationService.createNotification(
                client,
                coach,
                NotificationType.WORKOUT_ASSIGNED,
                20L,
                "WORKOUT_PLAN",
                20L,
                "workout_plan_details/20",
                "New Workout Assigned",
                "Coach assigned you a workout",
                "WORKOUT_ASSIGNED:10:20"
        );

        assertThat(response).isNull();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    void createNotification_savesFullMetadataAndMapsRecipientAndEntityFields() {
        when(notificationRepository.existsByRecipientIdAndEventKey(3L, "WORKOUT_ASSIGNED:10:20")).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(99L);
            return n;
        });

        NotificationResponse response = notificationService.createNotification(
                client,
                coach,
                NotificationType.WORKOUT_ASSIGNED,
                20L,
                "WORKOUT_PLAN",
                20L,
                "workout_plan_details/20",
                "New Workout Assigned",
                "Coach assigned you a workout",
                "WORKOUT_ASSIGNED:10:20"
        );

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(99L);
        assertThat(response.getRecipientUserId()).isEqualTo(3L);
        assertThat(response.getType()).isEqualTo(NotificationType.WORKOUT_ASSIGNED);
        assertThat(response.getRelatedEntityType()).isEqualTo("WORKOUT_PLAN");
        assertThat(response.getRelatedEntityId()).isEqualTo(20L);
        assertThat(response.getDeepLink()).isEqualTo("workout_plan_details/20");
        assertThat(response.isRead()).isFalse();
    }

    @Test
    void getNotification_andMarkAsRead_throwAccessDeniedForAnotherUsersNotification() {
        when(notificationRepository.findByIdAndRecipientId(77L, 3L)).thenReturn(Optional.empty());
        when(notificationRepository.existsById(77L)).thenReturn(true);

        assertThatThrownBy(() -> notificationService.getNotification(3L, 77L))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> notificationService.markAsRead(3L, 77L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void getNotification_throwsNotFoundWhenIdDoesNotExist() {
        when(notificationRepository.findByIdAndRecipientId(404L, 3L)).thenReturn(Optional.empty());
        when(notificationRepository.existsById(404L)).thenReturn(false);

        assertThatThrownBy(() -> notificationService.getNotification(3L, 404L))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void getNotifications_withPageAndSize_usesPageableRepositoryQuery() {
        Notification notification = Notification.builder()
                .id(55L)
                .recipient(client)
                .notificationType(NotificationType.ADMIN_ANNOUNCEMENT)
                .title("Platform Announcement")
                .message("Updates")
                .isRead(false)
                .build();
        Page<Notification> page = new PageImpl<>(List.of(notification));
        when(notificationRepository.findByRecipientIdOrderByCreatedAtDesc(eq(3L), any(Pageable.class)))
                .thenReturn(page);

        List<NotificationResponse> results = notificationService.getNotifications(3L, false, 0, 10);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getType()).isEqualTo(NotificationType.ADMIN_ANNOUNCEMENT);
        verify(notificationRepository).findByRecipientIdOrderByCreatedAtDesc(eq(3L), any(Pageable.class));
    }

    @Test
    void publishAdminAnnouncement_targetsCoachesWhenTargetRoleIsCoach() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(userRepository.findAll()).thenReturn(List.of(admin, coach, client));
        when(coachProfileRepository.findUserIdsWithProfiles(any())).thenReturn(Set.of(2L));
        when(notificationRepository.existsByRecipientIdAndEventKey(anyLong(), any())).thenReturn(false);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(100L);
            return n;
        });

        CreateAnnouncementRequest request = CreateAnnouncementRequest.builder()
                .title("Coach Update")
                .message("New client dashboard is live.")
                .targetRole("COACH")
                .eventKey("coach-release-v1")
                .build();

        int count = notificationService.publishAdminAnnouncement(1L, request);

        assertThat(count).isEqualTo(1);
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }
}
