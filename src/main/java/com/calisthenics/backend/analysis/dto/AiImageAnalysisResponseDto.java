package com.calisthenics.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiImageAnalysisResponseDto {

    private boolean success;
    private String message;

    @JsonProperty("pose_detected")
    private boolean poseDetected;

    @JsonProperty("pose_keypoints")
    private PoseKeypointsDto poseKeypoints;

    @JsonProperty("planche_analysis")
    private PlancheAnalysisResultDto plancheAnalysis;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PoseKeypointsDto {
        private Map<String, Object> keypoints;

        @JsonProperty("bounding_box")
        private List<Double> boundingBox;

        @JsonProperty("detection_confidence")
        private Double detectionConfidence;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlancheAnalysisResultDto {
        @JsonProperty("form_score")
        private BigDecimal formScore;

        @JsonProperty("is_valid_hold")
        private Boolean isValidHold;

        @JsonProperty("target_progression")
        private String targetProgression;

        @JsonProperty("detected_progression")
        private String detectedProgression;

        @JsonProperty("joint_angles")
        private JointAnglesDto jointAngles;

        @JsonProperty("alignment")
        private Map<String, Object> alignment;

        @JsonProperty("form_errors")
        private List<String> formErrors;

        @JsonProperty("positive_feedback")
        private List<String> positiveFeedback;

        @JsonProperty("actionable_cues")
        private List<String> actionableCues;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class JointAnglesDto {
        @JsonProperty("primary_elbow_deg")
        private BigDecimal primaryElbowDeg;

        @JsonProperty("primary_shoulder_lean_deg")
        private BigDecimal primaryShoulderLeanDeg;

        @JsonProperty("hip_angle_deg")
        private BigDecimal hipAngleDeg;

        @JsonProperty("body_horizontal_tilt_deg")
        private BigDecimal bodyHorizontalTiltDeg;
    }
}
