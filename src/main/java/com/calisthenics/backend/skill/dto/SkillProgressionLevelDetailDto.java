package com.calisthenics.backend.skill.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillProgressionLevelDetailDto {
    private Long id;
    private int levelNumber;
    private String code;
    private String title;
    private DifficultyLevel difficulty;
    private String targetCriteria;
    private String description;
    @Builder.Default
    private List<SkillExerciseDto> exercises = new ArrayList<>();
}
