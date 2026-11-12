package com.calisthenics.backend.adminai.entity;

import com.calisthenics.backend.adminai.type.AdminRecommendationPriority;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "admin_ai_recommendations",
        indexes = {
                @Index(name = "idx_admin_ai_rec_status_type", columnList = "status, recommendation_type, created_at DESC")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminAiRecommendation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_type", nullable = false, length = 40)
    private AdminRecommendationType recommendationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private AdminRecommendationStatus status = AdminRecommendationStatus.PENDING_APPROVAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private AdminRecommendationPriority priority = AdminRecommendationPriority.MEDIUM;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private AdminAiConversation conversation;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_admin_id")
    private User createdByAdmin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_admin_id")
    private User reviewedByAdmin;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @Column(name = "created_resource_id")
    private Long createdResourceId;

    @Column(name = "created_resource_type", length = 40)
    private String createdResourceType;

    @Column(name = "reviewed_at")
    private ZonedDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
