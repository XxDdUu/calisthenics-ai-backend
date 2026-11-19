package com.calisthenics.backend.aichat.dto.preview;

import com.calisthenics.backend.aichat.dto.AiMessageResponse;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkoutPlanChangePreviewActionResponse {
    private boolean success;
    private boolean mutated;
    private boolean verified;
    private boolean stale;
    private String code;
    private String message;
    private WorkoutPlanChangePreviewResponse preview;
    private WorkoutPlanChangePreviewResponse refreshedPreview;
    private AiMessageResponse assistantMessage;
}
