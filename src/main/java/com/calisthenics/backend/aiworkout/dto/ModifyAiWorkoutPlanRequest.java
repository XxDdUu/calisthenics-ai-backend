package com.calisthenics.backend.aiworkout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModifyAiWorkoutPlanRequest {

    @NotBlank(message = "Modification prompt cannot be blank")
    @Size(min = 2, max = 2000, message = "Modification prompt must be between 2 and 2000 characters")
    private String prompt;

    @NotNull(message = "Current plan must be provided for modification")
    private AiWorkoutPlanPreviewResponse currentPlan;

    private Long clientId;
}
