package com.calisthenics.backend.coachclient.dto.request;

import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateWorkoutPlanAssignmentStatusRequest {

    @NotNull(message = "Assignment status is required")
    private WorkoutPlanAssignmentStatus status;
}
