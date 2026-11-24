package com.calisthenics.backend.exercisetree.entity;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "exercise_progression_edges",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exercise_progression_edge",
                columnNames = {"source_exercise_id", "target_exercise_id", "training_type", "relation_type"}
        ),
        indexes = {
                @Index(name = "idx_exercise_progression_edges_source", columnList = "source_exercise_id, training_type"),
                @Index(name = "idx_exercise_progression_edges_target", columnList = "target_exercise_id, training_type")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseProgressionEdge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_exercise_id", nullable = false)
    private Exercise sourceExercise;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_exercise_id", nullable = false)
    private Exercise targetExercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    private ExerciseTreeCategoryType trainingType;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    private ProgressionRelationType relationType;

    @Column(name = "branch_label", length = 120)
    private String branchLabel;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
