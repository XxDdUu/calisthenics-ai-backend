package com.calisthenics.backend.workoutplan.dto.response;

import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String description;
    private WorkoutSplitType splitType;
    private boolean active;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private List<WorkoutPlanDayResponse> days;

    /** API-friendly name matching the persisted is_active concept. */
    @JsonProperty("isActive")
    public boolean isActive() {
        return active;
    }
}
