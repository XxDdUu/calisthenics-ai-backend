package com.calisthenics.backend.review.entity;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(
        name = "coach_reviews",
        uniqueConstraints = @UniqueConstraint(name = "uk_coach_reviews_coach_student", columnNames = {"coach_id", "student_id"}),
        indexes = {
                @Index(name = "idx_coach_reviews_coach_status_created", columnList = "coach_id, status, created_at"),
                @Index(name = "idx_coach_reviews_status_created", columnList = "status, created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(name = "coaching_quality")
    private Integer coachingQuality;

    @Column(name = "communication")
    private Integer communication;

    @Column(name = "plan_effectiveness")
    private Integer planEffectiveness;

    @Column(name = "support_responsiveness")
    private Integer supportResponsiveness;

    @Column(length = 1000)
    private String comment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private CoachReviewStatus status = CoachReviewStatus.VISIBLE;

    @Column(name = "moderation_reason", length = 500)
    private String moderationReason;

    @Column(name = "moderated_by_admin_id")
    private Long moderatedByAdminId;

    @Column(name = "moderated_at")
    private Instant moderatedAt;

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
