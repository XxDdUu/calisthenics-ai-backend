package com.calisthenics.backend.gettingstarted.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Structured edit operation on a Getting Started program day or exercise.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GettingStartedExerciseOperationRequest {

    /** ADD_EXERCISE, REMOVE_EXERCISE, UPDATE_EXERCISE, REORDER_EXERCISES, UPDATE_DAY */
    private String operation;
    private Integer dayNumber;
    private String dayTitle;
    private String dayDescription;

    private Long exerciseId;
    private String exerciseName;
    private String targetExerciseName;
    private Integer sets;
    private Integer reps;
    private Integer durationSeconds;
    private Integer restSeconds;
    private String instructions;
    private Integer displayOrder;
    private Integer newOrder;

    private List<Long> orderedExerciseIds;
    private List<String> orderedExerciseNames;
}
