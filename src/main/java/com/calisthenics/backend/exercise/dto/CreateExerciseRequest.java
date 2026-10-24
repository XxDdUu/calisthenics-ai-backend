package com.calisthenics.backend.exercise.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateExerciseRequest {

    @NotBlank(message = "Exercise name is required")
    private String name;

    @NotBlank(message = "Exercise code is required")
    private String code;

    @NotNull(message = "Exercise category is required")
    private ExerciseCategory category;

    @NotNull(message = "Difficulty level is required")
    private DifficultyLevel difficulty;

    private String description;
    private String targetMuscles;
    private String instructions;
}
