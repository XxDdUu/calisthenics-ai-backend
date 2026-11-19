package com.calisthenics.backend.skill.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;
import com.calisthenics.backend.exercise.muscle.ExerciseMuscleTargetDto;
import com.calisthenics.backend.skill.entity.SkillRelationType;
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
public class SkillExerciseDto {
    private Long id;
    private String name;
    private String code;
    private ExerciseCategory category;
    private ExerciseTrainingType trainingType;
    private String subcategory;
    private DifficultyLevel difficulty;
    private String description;
    private String equipment;
    private String instructions;
    private String targetMuscles;
    private String secondaryMuscles;
    @Builder.Default
    private List<String> primaryMuscleIds = new ArrayList<>();
    @Builder.Default
    private List<String> secondaryMuscleIds = new ArrayList<>();
    @Builder.Default
    private List<ExerciseMuscleTargetDto> muscleTargets = new ArrayList<>();
    @Builder.Default
    private List<ExerciseMediaResponse> mediaItems = new ArrayList<>();
    private Integer recommendedSets;
    private String recommendedReps;
    private Integer recommendedRestSeconds;
    private String commonMistakes;
    private String safetyConsiderations;
    private String progressionNotes;
    private SkillRelationType relationType;
    private Integer progressionLevelNumber;
    private String progressionLevelCode;
    private String progressionLevelTitle;
    private boolean videoAnalysisSupported;
    private String analysisExerciseCode;
    @Builder.Default
    private List<String> supportedSkillCodes = new ArrayList<>();
    @Builder.Default
    private List<String> supportedSkillNames = new ArrayList<>();
}
