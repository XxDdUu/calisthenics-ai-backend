package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import com.calisthenics.backend.exercise.dto.ExerciseMediaResponse;
import com.calisthenics.backend.exercise.muscle.ExerciseMuscleTargetDto;
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
public class ExerciseTreeExerciseDetailResponse {
    private Long nodeId;
    private Long exerciseId;
    private String code;
    private String name;
    private ExerciseTrainingType exerciseTrainingType;
    private ExerciseCategory exerciseCategory;
    private ExerciseTreeCategoryType treeCategory;
    private String subcategoryKey;
    private String subcategoryDisplayName;
    private DifficultyLevel catalogDifficulty;
    private DifficultyLevel progressionDifficulty;
    private boolean difficultyValidated;
    private String branchKey;
    private String branchLabel;
    private String description;
    private String instructions;
    private String equipment;
    private String targetMuscles;
    private String secondaryMuscles;
    private List<String> primaryMuscles;
    private List<String> primaryMuscleIds;
    private List<String> secondaryMuscleIds;
    private List<ExerciseMuscleTargetDto> muscleTargets;
    private String imageUrl;
    private String videoUrl;
    private List<ExerciseMediaResponse> mediaItems;
    private Integer recommendedSets;
    private String recommendedReps;
    private Integer recommendedRestSeconds;
    private String commonMistakes;
    private String safetyConsiderations;
    private String progressionNotes;
    private Boolean videoAnalysisSupported;
    private MasteryCriteriaDto masteryCriteria;
    private ExerciseNodeProgressDto progress;
    private List<ExerciseProgressionLinkDto> previousProgressions;
    private List<ExerciseProgressionLinkDto> nextProgressions;
    private List<ExerciseProgressionLinkDto> alternativeProgressions;
    private List<ExerciseProgressionLinkDto> regressions;
    private List<ExerciseProgressionLinkDto> relatedSkills;
    private List<ExerciseProgressionLinkDto> prerequisites;
    private List<ExercisePlacementDto> placements;
    private List<String> aiPromptSuggestions;
    private boolean needsManualReview;
    private String reviewNotes;
}
