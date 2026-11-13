package com.calisthenics.backend.adminai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiAdminAgentOutput {

    @JsonProperty("intent")
    private String intent;

    @JsonProperty("reply")
    private String reply;

    @JsonProperty("target_skill")
    private String targetSkill;

    @JsonProperty("target_level")
    private String targetLevel;

    @JsonProperty("generate_exercise_recommendations")
    private Boolean generateExerciseRecommendations;

    @JsonProperty("generate_workout_program")
    private Boolean generateWorkoutProgram;

    @JsonProperty("generate_operational_insights")
    private Boolean generateOperationalInsights;

    @JsonProperty("generate_recommendations")
    private Boolean generateRecommendations;

    @JsonProperty("suggested_follow_ups")
    private List<String> suggestedFollowUps;
}
