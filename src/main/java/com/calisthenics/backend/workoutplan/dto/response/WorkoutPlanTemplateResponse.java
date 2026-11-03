package com.calisthenics.backend.workoutplan.dto.response;

import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlanTemplateResponse {

    private WorkoutSplitType splitType;
    private List<WorkoutPlanDayResponse> days;
}
