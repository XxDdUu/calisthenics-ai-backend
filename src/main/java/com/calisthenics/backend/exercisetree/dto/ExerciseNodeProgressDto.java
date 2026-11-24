package com.calisthenics.backend.exercisetree.dto;

import com.calisthenics.backend.exercisetree.type.ExerciseNodeProgressStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseNodeProgressDto {
    private Long exerciseId;
    private ExerciseNodeProgressStatus status;
    private int progressPercent;
    private int completedSessions;
    private int qualifyingSessions;
    private int requiredSessions;
    private Integer bestReps;
    private Integer bestHoldSeconds;
    private Double bestWeightKg;
    private int personalRecordCount;
    private boolean prerequisitesMet;
    private List<Long> unmetPrerequisiteExerciseIds;
    private String statusReason;
}
