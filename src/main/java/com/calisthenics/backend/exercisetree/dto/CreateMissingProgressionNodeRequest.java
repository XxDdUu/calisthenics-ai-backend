package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateMissingProgressionNodeRequest {
    @NotNull
    private ExerciseTreeCategoryType trainingType;
    @NotBlank
    private String subcategoryKey;
    @NotBlank
    private String branchKey;
    @NotBlank
    private String suggestedName;
    @NotNull
    private DifficultyLevel targetDifficulty;
    private String afterExerciseCode;
    private String beforeExerciseCode;
    private String reason;
}
