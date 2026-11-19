package com.calisthenics.backend.aichat.entity;

import com.calisthenics.backend.aichat.type.WorkoutPlanPreviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "ai_workout_plan_change_previews",
        indexes = {
                @Index(
                        name = "idx_ai_wp_previews_user_conv_status",
                        columnList = "user_id, conversation_id, status, created_at DESC"
                ),
                @Index(
                        name = "idx_ai_wp_previews_preview_id_user",
                        columnList = "preview_id, user_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiWorkoutPlanChangePreview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "preview_id", nullable = false, unique = true, length = 64)
    private String previewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "workout_plan_id", nullable = false)
    private Long workoutPlanId;

    @Column(name = "assistant_message_id")
    private Long assistantMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    @Builder.Default
    private WorkoutPlanPreviewStatus status = WorkoutPlanPreviewStatus.PENDING;

    @Column(nullable = false)
    @Builder.Default
    private Integer revision = 1;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String summary;

    @Column(name = "source_prompt", columnDefinition = "TEXT")
    private String sourcePrompt;

    @Column(name = "plan_fingerprint", nullable = false, length = 128)
    private String planFingerprint;

    @Column(name = "operations_json", nullable = false, columnDefinition = "TEXT")
    private String operationsJson;

    @Column(name = "preview_payload_json", nullable = false, columnDefinition = "TEXT")
    private String previewPayloadJson;

    @Column(name = "failure_reason", columnDefinition = "TEXT")
    private String failureReason;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;
}
