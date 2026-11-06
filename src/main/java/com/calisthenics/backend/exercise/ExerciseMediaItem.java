package com.calisthenics.backend.exercise;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "exercise_media_items",
        indexes = @Index(name = "idx_exercise_media_items_exercise_order", columnList = "exercise_id, display_order, id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseMediaItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type", nullable = false, length = 16)
    private ExerciseMediaType type;

    @Column(nullable = false, length = 2048)
    private String url;

    @Column(name = "thumbnail_url", length = 2048)
    private String thumbnailUrl;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;
}
