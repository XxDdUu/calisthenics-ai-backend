package com.calisthenics.backend.exercisetree.entity;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.MasteryCriteriaType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "exercise_tree_nodes",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exercise_tree_node_placement",
                columnNames = {"exercise_id", "training_type", "subcategory_key"}
        ),
        indexes = {
                @Index(name = "idx_exercise_tree_nodes_type_sub", columnList = "training_type, subcategory_key, tree_tier, branch_order"),
                @Index(name = "idx_exercise_tree_nodes_exercise", columnList = "exercise_id"),
                @Index(name = "idx_exercise_tree_nodes_review", columnList = "needs_manual_review")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseTreeNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    private ExerciseTreeCategoryType trainingType;

    @Column(name = "subcategory_key", nullable = false, length = 40)
    private String subcategoryKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "progression_difficulty", nullable = false, length = 24)
    private DifficultyLevel progressionDifficulty;

    @Column(name = "difficulty_validated", nullable = false)
    @Builder.Default
    private boolean difficultyValidated = false;

    @Column(name = "tree_tier", nullable = false)
    @Builder.Default
    private Integer treeTier = 1;

    @Column(name = "branch_key", nullable = false, length = 64)
    private String branchKey;

    @Column(name = "branch_label", nullable = false, length = 120)
    private String branchLabel;

    @Column(name = "branch_order", nullable = false)
    @Builder.Default
    private Integer branchOrder = 0;

    @Column(name = "is_primary_placement", nullable = false)
    @Builder.Default
    private boolean primaryPlacement = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "mastery_criteria_type", nullable = false, length = 32)
    @Builder.Default
    private MasteryCriteriaType masteryCriteriaType = MasteryCriteriaType.COMPLETED_SESSIONS;

    @Column(name = "mastery_target_reps")
    private Integer masteryTargetReps;

    @Column(name = "mastery_target_sets")
    private Integer masteryTargetSets;

    @Column(name = "mastery_target_hold_seconds")
    private Integer masteryTargetHoldSeconds;

    @Column(name = "mastery_target_weight_kg")
    private Double masteryTargetWeightKg;

    @Column(name = "mastery_min_completed_sessions", nullable = false)
    @Builder.Default
    private Integer masteryMinCompletedSessions = 3;

    @Column(name = "mastery_summary", length = 255)
    private String masterySummary;

    @Column(name = "needs_manual_review", nullable = false)
    @Builder.Default
    private boolean needsManualReview = false;

    @Column(name = "review_notes", columnDefinition = "TEXT")
    private String reviewNotes;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    public static int tierForDifficulty(DifficultyLevel difficulty) {
        if (difficulty == null) {
            return 1;
        }
        return switch (difficulty) {
            case BEGINNER -> 1;
            case INTERMEDIATE -> 2;
            case ADVANCED -> 3;
            case ELITE -> 4;
        };
    }
}
