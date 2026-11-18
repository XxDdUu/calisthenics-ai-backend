package com.calisthenics.backend.exercise.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseOrigin;
import com.calisthenics.backend.exercise.ExerciseStatus;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ExerciseResponse {

    private Long id;
    private String name;
    private String code;
    private ExerciseCategory category;
    private DifficultyLevel difficulty;
    private String description;
    private String targetMuscles;
    private String instructions;
    private String imageUrl;
    private String videoUrl;
    private String equipment;
    private boolean active;
    private ExerciseStatus status;
    private ExerciseOrigin origin;
    private List<ExerciseMediaResponse> mediaItems;
}
