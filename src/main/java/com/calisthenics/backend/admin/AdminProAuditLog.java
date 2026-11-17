package com.calisthenics.backend.admin;

import com.calisthenics.backend.user.EntitlementGrantSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "admin_pro_audit_logs",
        indexes = {
                @Index(name = "idx_admin_pro_audit_target_created", columnList = "target_user_id, created_at DESC"),
                @Index(name = "idx_admin_pro_audit_admin_created", columnList = "admin_user_id, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminProAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "target_user_id", nullable = false)
    private Long targetUserId;

    @Column(name = "admin_user_id")
    private Long adminUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private AdminProAuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_source", nullable = false, length = 30)
    private EntitlementGrantSource grantSource;

    @Column(name = "previous_effective_tier", nullable = false, length = 20)
    private String previousEffectiveTier;

    @Column(name = "new_effective_tier", nullable = false, length = 20)
    private String newEffectiveTier;

    @Column(name = "previous_status", nullable = false, length = 20)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, length = 20)
    private String newStatus;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
