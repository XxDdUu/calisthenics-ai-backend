package com.calisthenics.backend.coachclient.dto.request;

import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
public class UpdateWorkoutPlanAssignmentRequest {

    private WorkoutPlanAssignmentStatus status;

    private LocalDate startDate;

    private LocalDate endDate;

    @Size(max = 4000, message = "Notes must be at most 4000 characters")
    private String notes;
}
