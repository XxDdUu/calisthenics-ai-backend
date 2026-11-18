package com.calisthenics.backend.adminai.dto.data;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExerciseDraftCandidateDto {

    @JsonProperty("name")
    private String name;

    @JsonProperty("code")
    private String code;

    @JsonProperty("category")
    private String category;

    @JsonProperty("difficulty")
    private String difficulty;

    @JsonProperty("description")
    private String description;

    @JsonProperty("instructions")
    private String instructions;

    @JsonProperty("target_muscles")
    private String targetMuscles;

    @JsonProperty("equipment")
    private String equipment;

    @JsonProperty("target_skill")
    private String targetSkill;
}
