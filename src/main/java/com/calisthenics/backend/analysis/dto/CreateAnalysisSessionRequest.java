package com.calisthenics.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAnalysisSessionRequest {
    private String sessionId;

    @NotBlank(message = "exerciseType is required")
    @JsonProperty("exerciseType")
    @JsonAlias({"exerciseCode", "exercise"})
    private String exerciseType;

    private Long exerciseId;

    private String cameraView;
}
