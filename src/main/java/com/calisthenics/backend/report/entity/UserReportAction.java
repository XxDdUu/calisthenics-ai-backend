package com.calisthenics.backend.report.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Append-only audit record of what an admin did on a report and when. */
@Entity
@Table(
        name = "user_report_actions",
        indexes = @Index(name = "idx_user_report_actions_report_created", columnList = "report_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserReportAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 24)
    private UserReportActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private UserReportStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 16)
    private UserReportStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "moderation_action", length = 24)
    private ModerationAction moderationAction;

    @Column(name = "suspended_until")
    private Instant suspendedUntil;

    @Column(length = 2000)
    private String note;

    @Column(name = "admin_id")
    private Long adminId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
