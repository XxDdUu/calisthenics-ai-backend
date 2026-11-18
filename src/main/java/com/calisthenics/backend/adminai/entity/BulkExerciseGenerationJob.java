package com.calisthenics.backend.adminai.entity;

import com.calisthenics.backend.adminai.type.BulkExerciseGenerationStatus;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "bulk_exercise_generation_jobs",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_bulk_ex_gen_admin_idemp", columnNames = {"admin_user_id", "idempotency_key"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkExerciseGenerationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_uid", nullable = false, unique = true, length = 64)
    private String jobUid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "admin_user_id", nullable = false)
    private User adminUser;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "conversation_id")
    private Long conversationId;

    @Column(name = "prompt", nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "training_type_filter", length = 32)
    private String trainingTypeFilter;

    @Column(name = "categories_filter", columnDefinition = "TEXT")
    private String categoriesFilter;

    @Column(name = "requested_quantity", nullable = false)
    private int requestedQuantity;

    @Column(name = "batch_size", nullable = false)
    @Builder.Default
    private int batchSize = 20;

    @Column(name = "generated_count", nullable = false)
    @Builder.Default
    private int generatedCount = 0;

    @Column(name = "duplicate_count", nullable = false)
    @Builder.Default
    private int duplicateCount = 0;

    @Column(name = "saved_count", nullable = false)
    @Builder.Default
    private int savedCount = 0;

    @Column(name = "failed_count", nullable = false)
    @Builder.Default
    private int failedCount = 0;

    @Column(name = "processed_batches", nullable = false)
    @Builder.Default
    private int processedBatches = 0;

    @Column(name = "total_batches_estimated", nullable = false)
    @Builder.Default
    private int totalBatchesEstimated = 1;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    @Builder.Default
    private BulkExerciseGenerationStatus status = BulkExerciseGenerationStatus.PENDING;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "summary_posted", nullable = false)
    @Builder.Default
    private boolean summaryPosted = false;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
