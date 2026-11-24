package com.calisthenics.backend.exercisetree.entity;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "exercise_tree_missing_nodes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exercise_tree_missing_node",
                columnNames = {"training_type", "subcategory_key", "suggested_name"}
        ),
        indexes = {
                @Index(name = "idx_exercise_tree_missing_nodes_type_sub", columnList = "training_type, subcategory_key")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseTreeMissingNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    private ExerciseTreeCategoryType trainingType;

    @Column(name = "subcategory_key", nullable = false, length = 40)
    private String subcategoryKey;

    @Column(name = "branch_key", nullable = false, length = 64)
    private String branchKey;

    @Column(name = "suggested_name", nullable = false, length = 160)
    private String suggestedName;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_difficulty", nullable = false, length = 24)
    private DifficultyLevel targetDifficulty;

    @Column(name = "after_exercise_code", length = 80)
    private String afterExerciseCode;

    @Column(name = "before_exercise_code", length = 80)
    private String beforeExerciseCode;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(name = "resolved_exercise_id")
    private Long resolvedExerciseId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
