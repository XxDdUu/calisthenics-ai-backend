package com.calisthenics.backend.workoutplan.dto.response;

import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlanResponse {

    private Long id;
    private String name;
    private WorkoutSplitType splitType;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private List<WorkoutPlanDayResponse> days;
}
