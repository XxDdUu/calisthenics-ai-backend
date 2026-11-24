package com.calisthenics.backend.exercisetree.entity;

import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "exercise_tree_categories",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exercise_tree_category",
                columnNames = {"training_type", "subcategory_key"}
        )
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseTreeCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    private ExerciseTreeCategoryType trainingType;

    @Column(name = "subcategory_key", nullable = false, length = 40)
    private String subcategoryKey;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
