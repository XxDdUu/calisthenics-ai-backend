package com.calisthenics.backend.report.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "user_reports",
        indexes = {
                @Index(name = "idx_user_reports_status_created", columnList = "status, created_at"),
                @Index(name = "idx_user_reports_reported", columnList = "reported_user_id, created_at"),
                @Index(name = "idx_user_reports_reporter_created", columnList = "reporter_id, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_user_id", nullable = false)
    private User reportedUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportReason reason;

    @Column(length = 2000)
    private String description;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "user_report_evidence", joinColumns = @JoinColumn(name = "report_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false, length = 500)
    @Builder.Default
    private List<String> evidenceUrls = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private UserReportStatus status = UserReportStatus.PENDING;

    @Column(name = "handled_by_admin_id")
    private Long handledByAdminId;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
