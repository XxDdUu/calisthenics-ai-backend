package com.calisthenics.backend.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseHistorySetDto {
    private Integer setNumber;
    private Integer reps;
    private Integer holdDurationSeconds;
    private BigDecimal externalWeight;
    private boolean completed;
}

