package com.calisthenics.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachFeedbackRequest {

    @JsonAlias({"feedback", "comment"})
    @Size(max = 2000, message = "Feedback message must be at most 2000 characters")
    private String message;

    @Min(value = 1, message = "Rating must be at least 1")
    @Max(value = 5, message = "Rating cannot exceed 5")
    private Integer rating;

    public CoachFeedbackRequest(String message) {
        this.message = message;
    }

    public String getEffectiveFeedback() {
        return message != null ? message.trim() : "";
    }

    // For backwards compatibility
    public String getFeedback() {
        return message;
    }

    public void setFeedback(String feedback) {
        this.message = feedback;
    }
}
