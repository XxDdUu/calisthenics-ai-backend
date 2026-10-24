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

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;
}
