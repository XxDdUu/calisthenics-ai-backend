package com.calisthenics.backend.skill.entity;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "skill_categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_categories_type_key",
                columnNames = {"training_type", "category_key"}
        ),
        indexes = {
                @Index(name = "idx_skill_categories_type", columnList = "training_type, display_order")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillCategoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    private ExerciseTrainingType trainingType;

    @Column(name = "category_key", nullable = false, length = 64)
    private String categoryKey;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
