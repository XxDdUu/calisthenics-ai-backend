package com.calisthenics.backend.skill.dto;

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
public class SkillDetailResponse {
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
    private boolean videoAnalysisSupported;
    private String liveAnalysisExerciseCode;
    @Builder.Default
    private List<SkillProgressionLevelDetailDto> progressionLevels = new ArrayList<>();
    @Builder.Default
    private List<SkillExerciseDto> relatedExercises = new ArrayList<>();
}
