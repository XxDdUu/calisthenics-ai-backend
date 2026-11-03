package com.calisthenics.backend.analytics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseHistorySessionDto {
    private Long sessionId;
    private LocalDate date;
    @Builder.Default
    private List<ExerciseHistorySetDto> sets = new ArrayList<>();
    private BigDecimal bestWeight;
    private Integer bestReps;
    private Integer bestHoldDuration;
    private BigDecimal totalVolume;
    private boolean isPersonalRecord;
    @Builder.Default
    private List<String> prTypes = new ArrayList<>();
}

