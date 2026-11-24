package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExercisePlacementDto {
    private Long nodeId;
    private ExerciseTreeCategoryType trainingType;
    private String subcategoryKey;
    private String subcategoryDisplayName;
    private DifficultyLevel progressionDifficulty;
    private String branchKey;
    private String branchLabel;
    private boolean primaryPlacement;
}
