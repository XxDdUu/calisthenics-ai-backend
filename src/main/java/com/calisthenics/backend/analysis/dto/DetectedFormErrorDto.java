package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetectedFormErrorDto {
    private String code;
    private String errorName;
    private Integer occurrences;
    private String severity; // LOW, MEDIUM, HIGH
    private List<Long> timestampsMs;
    private List<Integer> repNumbers;
    private String suggestedCorrection;
}
