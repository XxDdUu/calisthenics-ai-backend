package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiStructuredIssueDto {

    @JsonAlias("issue_code")
    private String code;

    @JsonAlias("metric_key")
    private String metric;

    @JsonAlias({"error_name", "errorName"})
    private String title;

    private String severity;

    @JsonAlias("description")
    private String message;

    @JsonAlias({"suggested_correction", "suggestedCorrection"})
    private String recommendation;

    @JsonAlias("measured_value")
    private Double measuredValue;

    @JsonAlias("target_range")
    private String targetRange;

    private Integer occurrences;

    @JsonAlias("timestamps_ms")
    private List<Long> timestampsMs;

    @JsonAlias({"affected_reps", "repNumbers", "rep_numbers"})
    private List<Integer> affectedReps;
}
