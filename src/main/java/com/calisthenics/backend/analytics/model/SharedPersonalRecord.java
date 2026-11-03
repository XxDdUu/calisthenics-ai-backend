package com.calisthenics.backend.analytics.model;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "shared_personal_records",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_shared_pr_coach",
                columnNames = {"personal_record_id", "coach_id"}
        ),
        indexes = {
                @Index(name = "idx_shared_prs_coach", columnList = "coach_id, shared_at"),
                @Index(name = "idx_shared_prs_client", columnList = "client_id, shared_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SharedPersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "personal_record_id", nullable = false)
    private PersonalRecord personalRecord;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coach_id", nullable = false)
    private User coach;

    @CreationTimestamp
    @Column(name = "shared_at", nullable = false, updatable = false)
    private ZonedDateTime sharedAt;

    @Column(name = "coach_feedback", columnDefinition = "TEXT")
    private String coachFeedback;

    @Column(name = "feedback_at")
    private ZonedDateTime feedbackAt;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String status = "SHARED";
}

