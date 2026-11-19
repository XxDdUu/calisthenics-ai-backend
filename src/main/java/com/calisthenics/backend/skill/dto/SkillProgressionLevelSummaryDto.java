package com.calisthenics.backend.skill.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillProgressionLevelSummaryDto {
    private Long id;
    private int levelNumber;
    private String code;
    private String title;
    private DifficultyLevel difficulty;
    private String targetCriteria;
    private String description;
    private int exerciseCount;
}
