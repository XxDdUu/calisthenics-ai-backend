package com.calisthenics.backend.aichat.dto.preview;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkoutPlanPreviewDayDiff {
    private Long dayId;
    private WorkoutDayOfWeek dayOfWeek;
    private String dayTitle;
    @Builder.Default
    private List<WorkoutPlanPreviewItem> changes = new ArrayList<>();
}
