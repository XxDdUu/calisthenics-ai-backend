package com.calisthenics.backend.exercisetree.dto;

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
public class ExerciseProgressionEdgeDto {
    private Long edgeId;
    private Long sourceExerciseId;
    private String sourceExerciseCode;
    private String sourceExerciseName;
    private Long targetExerciseId;
    private String targetExerciseCode;
    private String targetExerciseName;
    private ExerciseTreeCategoryType trainingType;
    private ProgressionRelationType relationType;
    private String branchLabel;
    private String notes;
    private int displayOrder;
}
