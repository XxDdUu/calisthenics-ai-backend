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
public class ExerciseTreeMissingNodeDto {
    private Long id;
    private ExerciseTreeCategoryType trainingType;
    private String subcategoryKey;
    private String branchKey;
    private String suggestedName;
    private DifficultyLevel targetDifficulty;
    private String afterExerciseCode;
    private String beforeExerciseCode;
    private String reason;
    private Long resolvedExerciseId;
}
