package com.calisthenics.backend.analytics.dto;

import com.calisthenics.backend.exercise.muscle.MuscleBodyView;
import com.calisthenics.backend.exercise.muscle.MuscleRegion;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MuscleVolumeDistributionDto {
    private String muscleId;
    private String displayName;
    private MuscleRegion region;
    private MuscleBodyView primaryView;
    private List<MuscleBodyView> views;
    private List<String> svgElementIds;
    private int displayOrder;
    private int primarySets;
    private int secondarySets;
    private double effectiveSets;
    private int primaryReps;
    private int secondaryReps;
    private int primaryHoldSeconds;
    private int secondaryHoldSeconds;
    private double primaryVolumeKg;
    private double secondaryVolumeKg;
    private double distributionPercentage;
    private double activationIntensity;
    private String trainingStatus;
}
