package com.calisthenics.backend.exercise.muscle;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseMuscleTargetDto {
    private String muscleId;
    private String displayName;
    private MuscleRole role;
    private MuscleRegion region;
    private MuscleBodyView primaryView;
    private List<MuscleBodyView> views;
    private List<String> svgElementIds;
    private int displayOrder;
}
