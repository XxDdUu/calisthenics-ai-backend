package com.calisthenics.backend.videoanalysis;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(
        name = "video_analysis_quota_usages",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_video_quota_user_date", columnNames = {"user_id", "quota_date"})
        },
        indexes = {
                @Index(name = "idx_video_quota_user_date", columnList = "user_id, quota_date")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoAnalysisQuotaUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Column(name = "used_count", nullable = false)
    @Builder.Default
    private Integer usedCount = 0;

    @Column(name = "daily_limit", nullable = false)
    private Integer dailyLimit;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;
}
