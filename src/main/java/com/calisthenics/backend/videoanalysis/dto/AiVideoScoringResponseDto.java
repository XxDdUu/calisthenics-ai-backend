package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiVideoScoringResponseDto {
    private String analysisId;
    private String status;
    private Object exerciseId;
    private String exercise;
    private Double score;
    private Double formAccuracy;
    private Double stability;
    private Double alignment;
    private Map<String, Object> metrics;
    private List<String> feedback;
    private AiVideoOutputMetadataDto video;
    private AiVideoKeypointsDataDto keypoints;
}
