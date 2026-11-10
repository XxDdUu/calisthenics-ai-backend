package com.calisthenics.backend.analysis;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "rep_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnore
    private AnalysisSession session;

    @Column(name = "rep_number", nullable = false)
    private Integer repNumber;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "timestamp_offset_ms")
    private Long timestampOffsetMs;

    @Column(name = "minimum_knee_angle", precision = 5, scale = 2)
    private BigDecimal minimumKneeAngle;

    @Column(name = "minimum_joint_angle", precision = 5, scale = 2)
    private BigDecimal minimumJointAngle;

    @Column(name = "form_score", precision = 5, scale = 2)
    private BigDecimal formScore;

    @Column(name = "depth_rating", length = 30)
    private String depthRating;

    @Column(name = "completion_status", length = 30)
    private String completionStatus;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Column(name = "detected_errors_json", columnDefinition = "TEXT")
    private String detectedErrorsJson;

    @Column(name = "rep_metrics_json", columnDefinition = "TEXT")
    private String repMetricsJson;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
