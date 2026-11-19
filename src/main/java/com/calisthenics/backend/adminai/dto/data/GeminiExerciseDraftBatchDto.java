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
public class GeminiExerciseDraftBatchDto {

    @JsonProperty("exercise_drafts")
    private List<ExerciseDraftCandidateDto> exerciseDrafts;
}
