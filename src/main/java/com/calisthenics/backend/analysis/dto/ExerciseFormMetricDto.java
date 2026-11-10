package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseFormMetricDto {
    private String name;
    private String label;
    private Double value;
    private String unit;
    private Double score;
    private Double confidence;
    private String status; // VALID, LOW_CONFIDENCE, UNAVAILABLE
    private String unavailableReason;
}
