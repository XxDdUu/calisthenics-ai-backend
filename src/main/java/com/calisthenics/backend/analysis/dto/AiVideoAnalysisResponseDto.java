package com.calisthenics.backend.analysis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiVideoAnalysisResponseDto {

    private boolean success;
    private String message;

    // Legacy fields (always present for backwards compat)
    @JsonProperty("total_frames")
    private Integer totalFrames;

    @JsonProperty("fps")
    private BigDecimal fps;

    @JsonProperty("duration_sec")
    private BigDecimal durationSec;

    @JsonProperty("processing_time_ms")
    private BigDecimal processingTimeMs;

    // Rich video metadata block (populated by enhanced AI service)
    @JsonProperty("video_metadata")
    private VideoMetadataDto videoMetadata;

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

    @JsonProperty("component_scores")
    private Map<String, BigDecimal> componentScores;

    @JsonProperty("stability")
    private StabilityMetricsDto stability;

    @JsonProperty("timeline")
    private List<FrameAnalysisDto> timeline;

    @JsonProperty("summary_feedback")
    private List<String> summaryFeedback;

    @JsonProperty("key_recommendations")
    private List<String> keyRecommendations;

    // ---- Nested DTOs ----

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class VideoMetadataDto {
        @JsonProperty("duration_ms")
        private Long durationMs;

        @JsonProperty("width")
        private Integer width;

        @JsonProperty("height")
        private Integer height;

        @JsonProperty("original_fps")
        private BigDecimal originalFps;

        @JsonProperty("total_frames")
        private Integer totalFrames;

        @JsonProperty("analyzed_frames")
        private Integer analyzedFrames;

        @JsonProperty("sampling_fps")
        private BigDecimal samplingFps;

        @JsonProperty("file_size_bytes")
        private Long fileSizeBytes;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StabilityMetricsDto {
        @JsonProperty("average_body_line_deviation")
        private BigDecimal averageBodyLineDeviation;

        @JsonProperty("max_body_line_deviation")
        private BigDecimal maxBodyLineDeviation;

        @JsonProperty("shoulder_position_variation")
        private BigDecimal shoulderPositionVariation;

        @JsonProperty("hip_position_variation")
        private BigDecimal hipPositionVariation;

        @JsonProperty("average_pose_confidence")
        private BigDecimal averagePoseConfidence;

        @JsonProperty("stability_score")
        private BigDecimal stabilityScore;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FrameAnalysisDto {
        @JsonProperty("frame_index")
        private Integer frameIndex;

        @JsonProperty("timestamp_sec")
        private BigDecimal timestampSec;

        @JsonProperty("pose_detected")
        private Boolean poseDetected;

        @JsonProperty("form_score")
        private BigDecimal formScore;

        @JsonProperty("is_valid_hold")
        private Boolean isValidHold;

        @JsonProperty("hold_state")
        private String holdState;

        @JsonProperty("detected_progression")
        private String detectedProgression;

        @JsonProperty("key_issues")
        private List<String> keyIssues;

        @JsonProperty("elbow_angle")
        private BigDecimal elbowAngle;

        @JsonProperty("shoulder_angle")
        private BigDecimal shoulderAngle;

        @JsonProperty("hip_alignment")
        private BigDecimal hipAlignment;

        @JsonProperty("body_line_deviation")
        private BigDecimal bodyLineDeviation;

        @JsonProperty("shoulder_lean")
        private BigDecimal shoulderLean;

        @JsonProperty("knee_angle")
        private BigDecimal kneeAngle;

        @JsonProperty("pose_confidence")
        private BigDecimal poseConfidence;
    }
}
