package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiMovementMetricDto {

    @JsonAlias("metric_key")
    private String key;

    private String name;

    @JsonAlias("display_name")
    private String label;

    @JsonAlias({"measured_value", "measuredValue"})
    private Double value;

    private Double min;

    private Double max;

    private Double avg;

    private String unit;

    @JsonAlias("target_range")
    private String targetRange;

    private Double score;

    private Double weight;

    private Double confidence;

    private String status;

    @JsonAlias({"unavailable_reason", "reason"})
    private String unavailableReason;
}
