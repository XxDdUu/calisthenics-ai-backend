package com.calisthenics.backend.adminai.dto.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GeminiBulkExerciseBatchOutput {

    @JsonProperty("exercises")
    private List<BulkGeneratedCandidateDto> exercises;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BulkGeneratedCandidateDto {
        @JsonProperty("name")
        private String name;

        @JsonProperty("category")
        private String category;

        @JsonProperty("training_type")
        private String trainingType;

        @JsonProperty("difficulty")
        private String difficulty;

        @JsonProperty("exercise_type")
        private String exerciseType;

        @JsonProperty("target_muscles")
        private String targetMuscles;

        @JsonProperty("secondary_muscles")
        private String secondaryMuscles;

        @JsonProperty("equipment")
        private String equipment;

        @JsonProperty("recommended_sets")
        private Integer recommendedSets;

        @JsonProperty("recommended_reps")
        private String recommendedReps;

        @JsonProperty("recommended_rest_seconds")
        private Integer recommendedRestSeconds;

        @JsonProperty("description")
        private String description;

        @JsonProperty("instructions")
        private String instructions;

        @JsonProperty("common_mistakes")
        private String commonMistakes;

        @JsonProperty("safety_considerations")
        private String safetyConsiderations;

        @JsonProperty("progression_notes")
        private String progressionNotes;

        @JsonProperty("aliases")
        private String aliases;
    }
}
