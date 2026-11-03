package com.calisthenics.backend.coachclient.dto.response;

import com.calisthenics.backend.coachclient.type.WorkoutPlanAssignmentStatus;
import com.calisthenics.backend.workoutplan.dto.response.WorkoutPlanResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkoutPlanAssignmentResponse {

    private Long id;
    private Long relationshipId;
    private Long coachId;
    private String coachName;
    private Long clientId;
    private String clientName;
    private WorkoutPlanAssignmentStatus status;
    private LocalDate startDate;
    private LocalDate endDate;
    private String notes;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private WorkoutPlanResponse workoutPlan;
}
