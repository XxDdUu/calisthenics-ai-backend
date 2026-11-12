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

    /**
     * Details when user is requesting to update their profile or body metrics
     */
    @JsonProperty("profile_update_action")
    private ProfileUpdateAction profileUpdateAction;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProfileUpdateAction {
        @JsonProperty("display_name")
        private String displayName;

        @JsonProperty("full_name")
        private String fullName;

        @JsonProperty("bio")
        private String bio;

        @JsonProperty("height_cm")
        private Double heightCm;

        @JsonProperty("weight_kg")
        private Double weightKg;

        @JsonProperty("experience_level")
        private String experienceLevel;

        @JsonProperty("training_experience")
        private String trainingExperience;

        @JsonProperty("primary_goals")
        private String primaryGoals;

        @JsonProperty("training_interests")
        private String trainingInterests;

        @JsonProperty("preferred_frequency")
        private String preferredFrequency;

        @JsonProperty("coaching_language")
        private String coachingLanguage;

        @JsonProperty("gender")
        private String gender;

        @JsonProperty("is_ambiguous")
        private Boolean isAmbiguous;

        @JsonProperty("clarification_question")
        private String clarificationQuestion;
    }

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
