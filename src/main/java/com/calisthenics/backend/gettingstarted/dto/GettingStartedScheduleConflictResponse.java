package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.workout.WorkoutSessionSource;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedScheduleConflictResponse {
    private LocalDate date;
    private Integer gettingStartedDayNumber;
    private String gettingStartedDayTitle;
    private String existingWorkoutTitle;
    private WorkoutSessionSource existingSource;
    private Long workoutSessionId;
    private Long workoutPlanId;
}
