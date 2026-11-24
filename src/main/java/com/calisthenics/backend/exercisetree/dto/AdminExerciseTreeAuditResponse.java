package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercise.DifficultyLevel;
import com.calisthenics.backend.exercise.ExerciseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminExerciseTreeAuditResponse {
    private int totalExistingExercises;
    private int publishedExercises;
    private int nonPublishedOrInactiveExercises;
    private int exercisesClassified;
    private int exercisesUnclassified;
    private int exercisesMissingMetadataCount;
    private int exercisesWithoutRelationshipsCount;
    private int exercisesRequiringManualReviewCount;
    private int totalProgressionEdges;
    private int missingProgressionNodesCount;
    private List<AuditExerciseItem> exercisesMissingMetadata;
    private List<AuditExerciseItem> exercisesWithoutRelationships;
    private List<AuditExerciseItem> exercisesRequiringManualReview;
    private List<ExerciseTreeMissingNodeDto> missingProgressionNodes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditExerciseItem {
        private Long exerciseId;
        private Long nodeId;
        private String code;
        private String name;
        private ExerciseStatus status;
        private boolean active;
        private DifficultyLevel catalogDifficulty;
        private DifficultyLevel progressionDifficulty;
        private String trainingType;
        private String subcategoryKey;
        private List<String> missingFields;
        private String reviewNotes;
    }
}
