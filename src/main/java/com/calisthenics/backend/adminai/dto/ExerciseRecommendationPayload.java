package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseRecommendationPayload {
    private String name;
    private String code;
    private ExerciseCategory category;
    private ExerciseTrainingType trainingType;
    private DifficultyLevel difficulty;
    private String targetSkill;
    private String progressionLevel;
    private String prerequisites;
    private String targetMuscles;
    private String equipment;
    private String aliases;
    private String description;
    private String instructions;
    private String whyAdd;
}
