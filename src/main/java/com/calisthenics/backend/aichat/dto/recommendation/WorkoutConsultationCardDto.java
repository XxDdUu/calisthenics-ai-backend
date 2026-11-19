package com.calisthenics.backend.aichat.dto.recommendation;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkoutConsultationCardDto {

    @Builder.Default
    private String type = "WORKOUT_CONSULTATION";
    @Builder.Default
    private boolean readOnly = true;
    @Builder.Default
    private boolean mutated = false;

    private String focusArea;
    private WorkoutDayOfWeek focusDayOfWeek;
    private Integer balanceScore;

    @Builder.Default
    private List<ConsultationFindingDto> findings = new ArrayList<>();
    @Builder.Default
    private List<String> undertrainedMuscles = new ArrayList<>();
    @Builder.Default
    private List<String> overloadedMuscles = new ArrayList<>();
    private String suggestedActionPrompt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConsultationFindingDto {
        private String severity; // "INFO", "WARNING"
        private String title;
        private String detail;
    }
}
