package com.calisthenics.backend.skill.entity;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "skill_category_memberships",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_cat_membership",
                columnNames = {"skill_id", "training_type", "category_key"}
        ),
        indexes = {
                @Index(name = "idx_skill_memberships_type_cat", columnList = "training_type, category_key, display_order")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillCategoryMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    private ExerciseTrainingType trainingType;

    @Column(name = "category_key", nullable = false, length = 64)
    private String categoryKey;

    @Column(name = "is_primary", nullable = false)
    @Builder.Default
    private boolean primary = true;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}
