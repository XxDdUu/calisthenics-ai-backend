package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import com.calisthenics.backend.exercisetree.type.ProgressionRelationType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExerciseProgressionEdgeRequest {
    @NotNull
    private Long sourceExerciseId;
    @NotNull
    private Long targetExerciseId;
    @NotNull
    private ExerciseTreeCategoryType trainingType;
    @NotNull
    private ProgressionRelationType relationType;
    private String branchLabel;
    private String notes;
    private Integer displayOrder;
}
