package com.calisthenics.backend.user;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "membership_entitlements",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_membership_entitlement_user_source", columnNames = {"user_id", "grant_source"})
        },
        indexes = {
                @Index(name = "idx_membership_entitlement_user_status", columnList = "user_id, status")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MembershipEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false, length = 20)
    @Builder.Default
    private MembershipTier tier = MembershipTier.PRO;

    @Enumerated(EnumType.STRING)
    @Column(name = "grant_source", nullable = false, length = 30)
    private EntitlementGrantSource grantSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EntitlementStatus status;

    @Column(name = "unlimited", nullable = false)
    @Builder.Default
    private boolean unlimited = true;

    @Column(name = "granted_by_admin_id")
    private Long grantedByAdminId;

    @Column(name = "granted_at", nullable = false)
    private ZonedDateTime grantedAt;

    @Column(name = "revoked_by_admin_id")
    private Long revokedByAdminId;

    @Column(name = "revoked_at")
    private ZonedDateTime revokedAt;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
