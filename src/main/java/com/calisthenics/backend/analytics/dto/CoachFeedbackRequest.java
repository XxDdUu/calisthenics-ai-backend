package com.calisthenics.backend.analytics.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachFeedbackRequest {
    @NotBlank(message = "Feedback cannot be blank")
    private String feedback;
}

