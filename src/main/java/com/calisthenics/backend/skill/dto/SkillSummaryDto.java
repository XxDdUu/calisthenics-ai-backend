package com.calisthenics.backend.skill.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillSummaryDto {
    private Long id;
    private String code;
    private String name;
    private ExerciseTrainingType trainingType;
    private String categoryKey;
    private String categoryDisplayName;
    @Builder.Default
    private List<String> categoryKeys = new ArrayList<>();
    private String subcategory;
    private String description;
    private int displayOrder;
    private int progressionLevelCount;
    private int relatedExerciseCount;
    private boolean videoAnalysisSupported;
    private String liveAnalysisExerciseCode;
    @Builder.Default
    private List<DifficultyLevel> availableDifficulties = new ArrayList<>();
    @Builder.Default
    private List<SkillProgressionLevelSummaryDto> progressionLevels = new ArrayList<>();
}
