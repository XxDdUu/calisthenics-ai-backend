package com.calisthenics.backend.exercisetree.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseTreeUserProgressResponse {
    private Long userId;
    private int totalTrackedExercises;
    private int masteredCount;
    private int inProgressCount;
    private int availableCount;
    private int lockedCount;
    private Map<Long, ExerciseNodeProgressDto> progressByExerciseId;
    private List<ExerciseNodeProgressDto> nodes;
}
