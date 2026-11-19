package com.calisthenics.backend.aichat.dto.preview;

import com.calisthenics.backend.aichat.type.WorkoutPlanOperationType;
import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkoutPlanChangeOperation {
    private WorkoutPlanOperationType operationType;
    private Long sourceDayId;
    private WorkoutDayOfWeek sourceDayOfWeek;
    private String sourceDayLabel;
    private Long destinationDayId;
    private WorkoutDayOfWeek destinationDayOfWeek;
    private String destinationDayLabel;
    private Long workoutPlanExerciseId;
    private Long exerciseId;
    private String exerciseName;
    private ExercisePrescriptionValues previousValues;
    private ExercisePrescriptionValues proposedValues;
    private String summary;
}
