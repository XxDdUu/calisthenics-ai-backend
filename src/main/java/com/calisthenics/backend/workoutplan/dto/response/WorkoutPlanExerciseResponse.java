package com.calisthenics.backend.workoutplan.dto.response;

import com.calisthenics.backend.exercise.dto.ExerciseResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlanExerciseResponse {

    private Long id;
    private ExerciseResponse exercise;
    private Integer sets;
    private Integer reps;
    private Integer durationSeconds;
    private Integer restSeconds;
    private Integer displayOrder;
    private String notes;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
