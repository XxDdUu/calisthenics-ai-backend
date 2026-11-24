package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercisetree.type.MasteryCriteriaType;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateExerciseTreeNodeRequest {
    private String subcategoryKey;
    private DifficultyLevel progressionDifficulty;
    private Boolean difficultyValidated;
    private Boolean syncCatalogDifficulty;
    private String branchKey;
    private String branchLabel;
    private Integer branchOrder;
    private MasteryCriteriaType masteryCriteriaType;
    @Min(1)
    private Integer masteryTargetReps;
    @Min(1)
    private Integer masteryTargetSets;
    @Min(1)
    private Integer masteryTargetHoldSeconds;
    private Double masteryTargetWeightKg;
    @Min(1)
    private Integer masteryMinCompletedSessions;
    private String masterySummary;
    private Boolean needsManualReview;
    private String reviewNotes;
}
