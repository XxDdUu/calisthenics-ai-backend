package com.calisthenics.backend.analysis;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Analysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(name = "media_url", nullable = false)
    private String mediaUrl;

    @Column(name = "media_type", nullable = false)
    @Builder.Default
    private String mediaType = "IMAGE"; // IMAGE or VIDEO

    @Column(name = "overall_score")
    private BigDecimal overallScore;

    @Column(name = "hold_duration_seconds")
    private BigDecimal holdDurationSeconds;

    @Column(name = "form_feedback", columnDefinition = "TEXT")
    private String formFeedback;

    @Column(name = "elbow_angle")
    private BigDecimal elbowAngle;

    @Column(name = "body_line_angle")
    private BigDecimal bodyLineAngle;

    @Column(name = "shoulder_lean_angle")
    private BigDecimal shoulderLeanAngle;

    @Column(name = "raw_keypoints_json", columnDefinition = "TEXT")
    private String rawKeypointsJson;

    // --- Video metadata fields (populated for VIDEO media type) ---

    @Column(name = "video_duration_ms")
    private Long videoDurationMs;

    @Column(name = "video_width")
    private Integer videoWidth;

    @Column(name = "video_height")
    private Integer videoHeight;

    @Column(name = "original_fps")
    private BigDecimal originalFps;

    @Column(name = "total_frames")
    private Integer totalFrames;

    @Column(name = "analyzed_frames")
    private Integer analyzedFrames;

    @Column(name = "sampling_fps")
    private BigDecimal samplingFps;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "processing_time_ms")
    private BigDecimal processingTimeMs;

    // --- Aggregated biomechanical data stored as JSON blobs ---

    /** JSON object of component scores: {"elbow_extension": 85, "hip_extension": 70, ...} */
    @Column(name = "component_scores_json", columnDefinition = "TEXT")
    private String componentScoresJson;

    /** JSON object of stability metrics */
    @Column(name = "stability_json", columnDefinition = "TEXT")
    private String stabilityJson;

    /** JSON array of per-frame analysis metrics for timeline charts */
    @Column(name = "timeline_json", columnDefinition = "TEXT")
    private String timelineJson;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
