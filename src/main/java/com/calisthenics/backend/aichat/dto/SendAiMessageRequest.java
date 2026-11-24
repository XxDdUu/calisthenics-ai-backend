package com.calisthenics.backend.aichat.dto;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendAiMessageRequest {

    @NotBlank(message = "Message cannot be blank")
    private String message;

    private String timezone;

    private WorkoutDayOfWeek selectedDayOfWeek;

    private Long exerciseId;

    public SendAiMessageRequest(String message, String timezone) {
        this.message = message;
        this.timezone = timezone;
    }

    public SendAiMessageRequest(String message, String timezone, WorkoutDayOfWeek selectedDayOfWeek) {
        this.message = message;
        this.timezone = timezone;
        this.selectedDayOfWeek = selectedDayOfWeek;
    }
}
