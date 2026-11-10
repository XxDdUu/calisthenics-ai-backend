package com.calisthenics.backend.analysis;

import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "analysis_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisSession {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "exercise_type", nullable = false, length = 50)
    private String exerciseType;

    @Column(name = "exercise_id")
    private Long exerciseId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_seconds", precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal durationSeconds = BigDecimal.ZERO;

    @Column(name = "total_reps")
    @Builder.Default
    private Integer totalReps = 0;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    @Column(name = "average_form_score", precision = 5, scale = 2)
    private BigDecimal averageFormScore;

    @Column(name = "best_form_score", precision = 5, scale = 2)
    private BigDecimal bestFormScore;

    @Column(name = "lowest_form_score", precision = 5, scale = 2)
    private BigDecimal lowestFormScore;

    @Column(name = "analysis_confidence", precision = 4, scale = 3)
    private BigDecimal analysisConfidence;

    @Column(name = "peak_hold_duration_seconds", precision = 8, scale = 2)
    private BigDecimal peakHoldDurationSeconds;

    @Column(name = "correct_reps")
    @Builder.Default
    private Integer correctReps = 0;

    @Column(name = "incorrect_reps")
    @Builder.Default
    private Integer incorrectReps = 0;

    @Column(name = "average_knee_angle", precision = 5, scale = 2)
    private BigDecimal averageKneeAngle;

    @Column(name = "deepest_knee_angle", precision = 5, scale = 2)
    private BigDecimal deepestKneeAngle;

    @Column(name = "average_joint_angle", precision = 5, scale = 2)
    private BigDecimal averageJointAngle;

    @Column(name = "deepest_joint_angle", precision = 5, scale = 2)
    private BigDecimal deepestJointAngle;

    @Column(name = "analysis_status", nullable = false, length = 30)
    @Builder.Default
    private String analysisStatus = "IN_PROGRESS";

    @Column(name = "camera_view", length = 30)
    @Builder.Default
    private String cameraView = "SIDE";

    @Column(name = "exercise_metrics_json", columnDefinition = "TEXT")
    private String exerciseMetricsJson;

    @Column(name = "detected_errors_json", columnDefinition = "TEXT")
    private String detectedErrorsJson;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Column(name = "ai_recommendations_json", columnDefinition = "TEXT")
    private String aiRecommendationsJson;

    @Column(name = "ai_summary_status", length = 30)
    @Builder.Default
    private String aiSummaryStatus = "PENDING";

    @Column(name = "original_video_url", columnDefinition = "TEXT")
    private String originalVideoUrl;

    @Column(name = "annotated_video_url", columnDefinition = "TEXT")
    private String annotatedVideoUrl;

    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("repNumber ASC")
    @Builder.Default
    private List<RepHistory> reps = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
