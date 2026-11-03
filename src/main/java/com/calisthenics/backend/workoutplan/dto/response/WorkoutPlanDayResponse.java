package com.calisthenics.backend.workoutplan.dto.response;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlanDayResponse {

    private Long id;
    private WorkoutDayOfWeek dayOfWeek;
    private WorkoutSessionType sessionType;
    private String name;
    private List<WorkoutPlanExerciseResponse> exercises;
}
