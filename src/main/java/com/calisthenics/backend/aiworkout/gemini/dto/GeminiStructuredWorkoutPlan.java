package com.calisthenics.backend.aiworkout.gemini.dto;

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
public class GeminiStructuredWorkoutPlan {

    @JsonProperty("plan_name")
    private String planName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("duration_weeks")
    private Integer durationWeeks;

    @JsonProperty("suggested_start_date")
    private String suggestedStartDate;

    @JsonProperty("split_type")
    private String splitType;

    @JsonProperty("sessions")
    private List<GeminiSession> sessions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiSession {
        @JsonProperty("day_of_week")
        private String dayOfWeek;

        @JsonProperty("title")
        private String title;

        @JsonProperty("is_rest_day")
        private Boolean restDay;

        @JsonProperty("exercises")
        private List<GeminiExercise> exercises;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GeminiExercise {
        @JsonProperty("exercise_id")
        private Long exerciseId;

        @JsonProperty("exercise_name")
        private String exerciseName;

        @JsonProperty("sets")
        private Integer sets;

        @JsonProperty("reps")
        private Integer reps;

        @JsonProperty("hold_seconds")
        private Integer holdSeconds;

        @JsonProperty("rest_seconds")
        private Integer restSeconds;

        @JsonProperty("notes")
        private String notes;
    }
}
