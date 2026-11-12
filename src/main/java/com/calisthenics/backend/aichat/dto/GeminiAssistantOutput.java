package com.calisthenics.backend.aichat.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GeminiAssistantOutput {

    /**
     * Detected intent: "WORKOUT_PLAN", "PR_RECORD", "PROGRESS_ANALYSIS", or "GENERAL_CHAT"
     */
    private String intent;

    /**
     * Helpful Markdown response to the user
     */
    private String reply;

    /**
     * Details when user is reporting or recording a Personal Record
     */
    @JsonProperty("pr_action")
    private PrAction prAction;

    /**
     * Details when user is requesting a workout plan or routine change
     */
    @JsonProperty("workout_plan_action")
    private WorkoutPlanAction workoutPlanAction;

    /**
     * Details when user is asking for training progress analysis or plateau detection
     */
    @JsonProperty("progress_analysis_action")
    private ProgressAnalysisAction progressAnalysisAction;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PrAction {
        @JsonProperty("exercise_name")
        private String exerciseName;

        @JsonProperty("value")
        private BigDecimal value;

        @JsonProperty("unit")
        private String unit;

        @JsonProperty("pr_type")
        private String prType;

        @JsonProperty("is_ambiguous")
        private Boolean isAmbiguous;

        @JsonProperty("notes")
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkoutPlanAction {
        @JsonProperty("prompt")
        private String prompt;

        @JsonProperty("fitness_level")
        private String fitnessLevel;

        @JsonProperty("duration_weeks")
        private Integer durationWeeks;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgressAnalysisAction {
        @JsonProperty("exercise_name")
        private String exerciseName;

        @JsonProperty("focus_area")
        private String focusArea;
    }
}
