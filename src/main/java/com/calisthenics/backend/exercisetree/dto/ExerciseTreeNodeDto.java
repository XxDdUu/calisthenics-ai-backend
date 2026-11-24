package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
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
public class ExerciseTreeNodeDto {
    private Long nodeId;
    private Long exerciseId;
    private String code;
    private String name;
    private ExerciseTreeCategoryType trainingType;
    private String subcategoryKey;
    private String subcategoryDisplayName;
    private DifficultyLevel progressionDifficulty;
    private DifficultyLevel catalogDifficulty;
    private boolean difficultyValidated;
    private int treeTier;
    private String branchKey;
    private String branchLabel;
    private int branchOrder;
    private boolean primaryPlacement;
    private String equipment;
    private String targetMuscles;
    private List<String> primaryMuscleIds;
    private List<String> secondaryMuscleIds;
    private String imageUrl;
    private String videoUrl;
    private boolean hasMedia;
    private Integer recommendedSets;
    private String recommendedReps;
    private Integer recommendedRestSeconds;
    private MasteryCriteriaDto masteryCriteria;
    private ExerciseNodeProgressDto progress;
    private boolean needsManualReview;
    private String reviewNotes;
}
