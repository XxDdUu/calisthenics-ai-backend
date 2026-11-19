package com.calisthenics.backend.skill.dto;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillCategoryDto {
    private Long id;
    private String code;
    private ExerciseTrainingType trainingType;
    private String categoryKey;
    private String displayName;
    private String description;
    private int displayOrder;
    private int skillCount;
    private int exerciseCount;
}
