package com.calisthenics.backend.exercisetree.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseTreeCategoriesResponse {
    private int totalServerExercises;
    private int calisthenicsExerciseCount;
    private int gymExerciseCount;
    private List<ExerciseTreeCategorySummaryDto> calisthenicsCategories;
    private List<ExerciseTreeCategorySummaryDto> gymCategories;
}
