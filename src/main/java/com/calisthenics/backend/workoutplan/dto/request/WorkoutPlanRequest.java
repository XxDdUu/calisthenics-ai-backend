package com.calisthenics.backend.workoutplan.dto.request;

import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public abstract class WorkoutPlanRequest {

    @NotBlank(message = "Plan name is required")
    @Size(max = 100, message = "Plan name must be at most 100 characters")
    private String name;

    @Size(max = 2000, message = "Plan description must be at most 2000 characters")
    private String description;

    private WorkoutSplitType splitType;

    /**
     * When omitted on create, the first plan for a user becomes active. Existing clients may
     * send either active or isActive.
     */
    @JsonAlias("isActive")
    private Boolean active;

    @NotNull(message = "Workout days are required")
    @Size(max = 7, message = "A workout plan cannot contain more than seven days")
    @Valid
    private List<WorkoutPlanDayRequest> days;

    public void setName(String name) {
        this.name = trimToNull(name);
    }

    public void setDescription(String description) {
        this.description = trimToNull(description);
    }

    protected String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
