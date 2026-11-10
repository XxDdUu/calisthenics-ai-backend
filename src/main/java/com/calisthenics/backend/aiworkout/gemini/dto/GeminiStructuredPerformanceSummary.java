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
public class GeminiStructuredPerformanceSummary {

    @JsonProperty("overall_assessment")
    private String overallAssessment;

    @JsonProperty("strengths")
    private List<String> strengths;

    @JsonProperty("weaknesses")
    private List<String> weaknesses;

    @JsonProperty("recommendations")
    private List<String> recommendations;

    @JsonProperty("next_session_focus")
    private String nextSessionFocus;
}
