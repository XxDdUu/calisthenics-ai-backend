package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseTreeResponse {
    private ExerciseTreeCategoryType trainingType;
    private String subcategoryKey;
    private String subcategoryDisplayName;
    private int totalServerExercises;
    private int treeExerciseCount;
    private int filteredExerciseCount;
    private List<ExerciseTreeCategorySummaryDto> categories;
    private List<ExerciseTreeNodeDto> nodes;
    private List<ExerciseProgressionEdgeDto> edges;
    private List<ExerciseTreeMissingNodeDto> missingNodes;
}
