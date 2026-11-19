package com.calisthenics.backend.aichat.dto.preview;

import com.calisthenics.backend.aichat.type.WorkoutPlanOperationType;
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
public class WorkoutPlanPreviewItem {
    /**
     * Visual operation badge: ADD, REMOVE, UPDATE, MOVE
     */
    private String badge;
    private WorkoutPlanOperationType operationType;
    private Long exerciseId;
    private String exerciseName;
    private String previousSummary;
    private String proposedSummary;
    private ExercisePrescriptionValues previousValues;
    private ExercisePrescriptionValues proposedValues;
    private String detailText;
}
