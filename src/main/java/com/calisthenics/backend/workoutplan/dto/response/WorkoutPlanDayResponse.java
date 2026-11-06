package com.calisthenics.backend.workoutplan.dto.response;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    /** Legacy field retained for existing Android clients. */
    private WorkoutSessionType sessionType;

    /** Preferred name for the optional predefined category. */
    private WorkoutSessionType workoutType;

    /** Legacy field retained for existing Android clients. */
    private String name;

    /** Preferred free-form name of the workout. */
    private String customWorkoutName;

    /** Display-safe value: custom name, category label, or Rest Day. */
    private String workoutName;

    private String description;
    private boolean restDay;
    private Integer displayOrder;
    private List<WorkoutPlanExerciseResponse> exercises;

    /** API-friendly name matching the persisted is_rest_day concept. */
    @JsonProperty("isRestDay")
    public boolean isRestDay() {
        return restDay;
    }
}
