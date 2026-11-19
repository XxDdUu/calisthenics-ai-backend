package com.calisthenics.backend.skill.entity;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "skills",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skills_type_name",
                columnNames = {"training_type", "name"}
        ),
        indexes = {
                @Index(name = "idx_skills_type_category", columnList = "training_type, category_key, display_order")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    private ExerciseTrainingType trainingType;

    @Column(name = "category_key", nullable = false, length = 64)
    private String categoryKey;

    @Column(length = 120)
    private String subcategory;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
