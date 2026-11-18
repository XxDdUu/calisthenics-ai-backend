package com.calisthenics.backend.adminai.entity;

import com.calisthenics.backend.adminai.type.BulkExerciseItemOutcome;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(name = "bulk_exercise_generation_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkExerciseGenerationItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private BulkExerciseGenerationJob job;

    @Column(name = "sequence_number")
    private Integer sequenceNumber;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "exercise_name", nullable = false, length = 120)
    private String exerciseName;

    @Column(name = "exercise_code", length = 64)
    private String exerciseCode;

    @Column(name = "training_type", length = 32)
    private String trainingType;

    @Column(name = "category", length = 32)
    private String category;

    @Column(name = "difficulty", length = 32)
    private String difficulty;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 32)
    private BulkExerciseItemOutcome outcome;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Column(name = "batch_number", nullable = false)
    private int batchNumber;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
