package com.calisthenics.backend.review.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** Append-only history row; never updated or deleted by application code. */
@Entity
@Table(
        name = "coach_review_events",
        indexes = @Index(name = "idx_coach_review_events_review_created", columnList = "review_id, created_at")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CoachReviewEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "review_id", nullable = false)
    private Long reviewId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 16)
    private CoachReviewEventType eventType;

    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "overall_rating", nullable = false)
    private Integer overallRating;

    @Column(length = 1000)
    private String comment;

    @Column(length = 500)
    private String note;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
