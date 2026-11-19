package com.calisthenics.backend.skill.dto;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.skill.entity.SkillRelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillReferenceDto {
    private Long skillId;
    private String skillCode;
    private String skillName;
    private ExerciseTrainingType trainingType;
    private String categoryKey;
    private String categoryDisplayName;
    private String subcategory;
    private SkillRelationType relationType;
    private Integer progressionLevelNumber;
    private String progressionLevelTitle;
}
