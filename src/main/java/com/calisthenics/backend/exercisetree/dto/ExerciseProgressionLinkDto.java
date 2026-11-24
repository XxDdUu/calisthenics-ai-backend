package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercisetree.type.ExerciseNodeProgressStatus;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseProgressionLinkDto {
    private Long edgeId;
    private Long exerciseId;
    private String code;
    private String name;
    private DifficultyLevel difficulty;
    private ExerciseTreeCategoryType trainingType;
    private String subcategoryKey;
    private ProgressionRelationType relationType;
    private String branchLabel;
    private String notes;
    private ExerciseNodeProgressStatus progressStatus;
}
