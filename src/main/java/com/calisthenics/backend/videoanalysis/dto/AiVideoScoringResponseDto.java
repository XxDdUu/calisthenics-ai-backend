package com.calisthenics.backend.videoanalysis.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
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

    @JsonAlias("schema_version")
    private String schemaVersion;

    @JsonAlias("analysis_id")
    private String analysisId;

    private String status;

    @JsonAlias("error_message")
    private String errorMessage;

    @JsonAlias("exercise_id")
    private Object exerciseId;

    private String exercise;

    @JsonAlias("exercise_code")
    private String exerciseCode;

    private Double score;

    @JsonAlias("form_accuracy")
    private Double formAccuracy;

    private Double stability;

    private Double alignment;

    @JsonAlias("rep_count")
    private Integer repCount;

    @JsonAlias("valid_reps")
    private Integer validReps;

    @JsonAlias("incomplete_reps")
    private Integer incompleteReps;

    private List<AiRepBreakdownDto> reps;

    @JsonAlias("movement_metrics")
    private Map<String, AiMovementMetricDto> movementMetrics;

    @JsonAlias("structured_issues")
    private List<AiStructuredIssueDto> issues;

    private List<String> recommendations;

    @JsonAlias("camera_quality")
    private AiCameraQualityDto cameraQuality;

    private Map<String, Object> metrics;
    private List<String> feedback;
    private AiVideoOutputMetadataDto video;
    private AiVideoKeypointsDataDto keypoints;
}
