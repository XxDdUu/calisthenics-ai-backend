package com.calisthenics.backend.aichat.dto.recommendation;

import com.fasterxml.jackson.annotation.JsonInclude;
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
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseRecommendationItemDto {
    private Long exerciseId;
    private String name;
    private String code;
    private String category;
    private String difficulty;

    @Builder.Default
    private List<String> primaryMuscleIds = new ArrayList<>();
    @Builder.Default
    private List<String> secondaryMuscleIds = new ArrayList<>();
    @Builder.Default
    private List<String> primaryMuscleNames = new ArrayList<>();
    @Builder.Default
    private List<String> secondaryMuscleNames = new ArrayList<>();

    private Integer recommendedSets;
    private Integer recommendedReps;
    private Integer recommendedDurationSeconds;
    private Integer recommendedRestSeconds;
    private String prescriptionSummary;

    private String equipment;
    private String imageUrl;
    private String rationale;

    @Builder.Default
    private List<String> fillsUndertrainedMuscles = new ArrayList<>();
    @Builder.Default
    private boolean alreadyInTargetDay = false;
    @Builder.Default
    private List<String> alreadyInPlanDays = new ArrayList<>();
    private String volumeWarning;
}
