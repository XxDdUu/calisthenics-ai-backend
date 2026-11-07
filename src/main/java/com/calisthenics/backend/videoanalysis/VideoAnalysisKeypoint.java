package com.calisthenics.backend.videoanalysis;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "video_analysis_keypoints")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VideoAnalysisKeypoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "video_analysis_id", nullable = false)
    private VideoAnalysis videoAnalysis;

    @Column(name = "frame_index", nullable = false)
    private Integer frameIndex;

    @Column(name = "timestamp_ms", nullable = false)
    private Long timestampMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "keypoints_json", nullable = false, columnDefinition = "jsonb")
    private String keypointsJson;
}
