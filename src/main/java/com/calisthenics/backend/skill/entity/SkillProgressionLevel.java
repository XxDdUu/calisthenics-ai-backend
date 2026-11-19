package com.calisthenics.backend.skill.entity;

import com.calisthenics.backend.exercise.DifficultyLevel;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "skill_progression_levels",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_skill_level_num", columnNames = {"skill_id", "level_number"}),
                @UniqueConstraint(name = "uk_skill_level_code", columnNames = {"skill_id", "code"})
        },
        indexes = {
                @Index(name = "idx_skill_levels_skill", columnList = "skill_id, level_number")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillProgressionLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Column(name = "level_number", nullable = false)
    private int levelNumber;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false, length = 120)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DifficultyLevel difficulty;

    @Column(name = "target_criteria", length = 255)
    private String targetCriteria;

    @Column(columnDefinition = "TEXT")
    private String description;
}
