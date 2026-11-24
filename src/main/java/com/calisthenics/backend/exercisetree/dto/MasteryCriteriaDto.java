package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercisetree.type.MasteryCriteriaType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MasteryCriteriaDto {
    private MasteryCriteriaType criteriaType;
    private Integer targetReps;
    private Integer targetSets;
    private Integer targetHoldSeconds;
    private Double targetWeightKg;
    private Integer minCompletedSessions;
    private String summary;
}
