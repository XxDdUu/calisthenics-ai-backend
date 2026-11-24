package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercisetree.type.ExerciseTreeCategoryType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseTreeCategorySummaryDto {
    private ExerciseTreeCategoryType trainingType;
    private String subcategoryKey;
    private String displayName;
    private String description;
    private int displayOrder;
    private int exerciseCount;
    private int missingNodeCount;
    private Map<String, Integer> difficultyCounts;
}
