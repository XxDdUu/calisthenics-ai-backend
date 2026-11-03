package com.calisthenics.backend.workoutplan.dto.request;

import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
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

    private WorkoutSplitType splitType;

    @NotNull(message = "At least one workout day is required")
    @Size(min = 1, max = 7, message = "A workout plan must contain between one and seven days")
    @Valid
    private List<WorkoutPlanDayRequest> days;

    public void setName(String name) {
        this.name = name == null ? null : name.trim();
    }
}
