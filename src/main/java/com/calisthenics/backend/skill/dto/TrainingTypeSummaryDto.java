package com.calisthenics.backend.skill.dto;

import com.calisthenics.backend.exercise.ExerciseTrainingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrainingTypeSummaryDto {
    private ExerciseTrainingType trainingType;
    private String displayName;
    private String description;
    private int categoryCount;
    private int skillCount;
    private int exerciseCount;
}
