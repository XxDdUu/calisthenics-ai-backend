package com.calisthenics.backend.adminai.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "admin_ai_audit_logs",
        indexes = {
                @Index(name = "idx_admin_ai_audit_admin_created", columnList = "admin_user_id, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAiAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Column(nullable = false, length = 64)
    private String action;

    @Column(name = "target_type", length = 64)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(columnDefinition = "TEXT")
    private String details;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
