package com.calisthenics.backend.adminai.entity;

import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import com.calisthenics.backend.adminai.type.AdminPendingActionType;
import com.calisthenics.backend.gettingstarted.entity.GettingStartedProgram;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "admin_ai_pending_actions",
        indexes = {
                @Index(name = "idx_admin_ai_pending_admin_conv_status",
                        columnList = "admin_user_id, conversation_id, status, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAiPendingAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "confirmation_token", nullable = false, unique = true, length = 64)
    private String confirmationToken;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private AdminAiConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 40)
    private AdminPendingActionType actionType;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private GettingStartedProgram program;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience", length = 30)
    private TargetAudience targetAudience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private AdminPendingActionStatus status = AdminPendingActionStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;
}
