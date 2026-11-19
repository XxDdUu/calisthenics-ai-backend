package com.calisthenics.backend.aichat.dto.preview;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExercisePrescriptionValues {
    private Integer sets;
    private Integer reps;
    private Integer durationSeconds;
    private Integer restSeconds;
    private Integer displayOrder;
    private String notes;

    // Optional day-level / reorder attributes
    private String dayName;
    private String sessionType;
    private Boolean restDay;
    private List<Long> orderedAssignmentIds;
    private List<String> orderedExerciseNames;
}
