package com.calisthenics.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiVideoAnalysisResponseDto {

    private boolean success;
    private String message;

    @JsonProperty("total_hold_duration_sec")
    private BigDecimal totalHoldDurationSec;

    @JsonProperty("valid_hold_duration_sec")
    private BigDecimal validHoldDurationSec;

    @JsonProperty("peak_form_score")
    private BigDecimal peakFormScore;

    @JsonProperty("average_form_score")
    private BigDecimal averageFormScore;

    @JsonProperty("primary_progression")
    private String primaryProgression;

    @JsonProperty("summary_feedback")
    private List<String> summaryFeedback;

    @JsonProperty("key_recommendations")
    private List<String> keyRecommendations;
}
