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
public class MuscleGroupDto {
    private String id;
    private String displayName;
    private MuscleRegion region;
    private MuscleBodyView primaryView;
    private List<MuscleBodyView> views;
    private List<String> svgElementIds;
    private int displayOrder;
}
