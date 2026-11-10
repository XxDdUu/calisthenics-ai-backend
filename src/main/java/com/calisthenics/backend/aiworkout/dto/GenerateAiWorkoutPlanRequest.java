package com.calisthenics.backend.aiworkout.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateAiWorkoutPlanRequest {

    @NotBlank(message = "Prompt cannot be blank")
    @Size(min = 3, max = 2000, message = "Prompt must be between 3 and 2000 characters")
    private String prompt;

    private Long clientId;

    private String fitnessLevel;

    private String preferredSplit;

    private LocalDate startDate;

    private Integer durationWeeks;
}
