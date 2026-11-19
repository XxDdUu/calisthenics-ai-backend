package com.calisthenics.backend.skill.entity;

import com.calisthenics.backend.exercise.Exercise;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "skill_exercise_links",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_skill_exercise_link",
                columnNames = {"skill_id", "exercise_id"}
        ),
        indexes = {
                @Index(name = "idx_skill_ex_links_skill", columnList = "skill_id, progression_level_id, display_order"),
                @Index(name = "idx_skill_ex_links_exercise", columnList = "exercise_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillExerciseLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "progression_level_id")
    private SkillProgressionLevel progressionLevel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "relation_type", nullable = false, length = 32)
    @Builder.Default
    private SkillRelationType relationType = SkillRelationType.PRIMARY;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}
