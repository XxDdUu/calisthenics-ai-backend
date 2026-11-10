package com.calisthenics.backend.aiworkout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommitAiWorkoutPlanRequest {

    @NotNull(message = "Plan preview is required for commitment")
    @Valid
    private AiWorkoutPlanPreviewResponse plan;

    private String idempotencyKey;

    private LocalDate startDate;

    @Builder.Default
    private Boolean replaceExisting = true;

    private Long expectedActivePlanId;

    private Long clientId;
}
