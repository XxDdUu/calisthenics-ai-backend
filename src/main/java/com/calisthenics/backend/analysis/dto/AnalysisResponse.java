package com.calisthenics.backend.analysis.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AnalysisResponse {

    private Long id;
    private Long userId;
    private Long exerciseId;
    private String exerciseName;
    private String mediaUrl;
    private String mediaType;
    private BigDecimal overallScore;
    private BigDecimal holdDurationSeconds;
    private String formFeedback;
    private BigDecimal elbowAngle;
    private BigDecimal bodyLineAngle;
    private BigDecimal shoulderLeanAngle;
    private String rawKeypointsJson;
    private ZonedDateTime createdAt;

    // --- Video metadata (populated for VIDEO analyses) ---
    private Long videoDurationMs;
    private Integer videoWidth;
    private Integer videoHeight;
    private BigDecimal originalFps;
    private Integer totalFrames;
    private Integer analyzedFrames;
    private BigDecimal samplingFps;
    private Long fileSizeBytes;
    private BigDecimal processingTimeMs;

    // --- Aggregated biomechanical data (JSON strings) ---
    /** JSON: {"elbow_extension": 85.0, "hip_extension": 70.0, "shoulder_position": 78.0, "body_alignment": 82.0, "stability": 75.0} */
    private String componentScoresJson;
    /** JSON: StabilityMetrics object */
    private String stabilityJson;
    /** JSON: Array of per-frame VideoFrameAnalysis objects for timeline charts */
    private String timelineJson;
}
