package com.calisthenics.backend.gettingstarted.entity;

import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "getting_started_programs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedProgram {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_audience", nullable = false, length = 30)
    @Builder.Default
    private TargetAudience targetAudience = TargetAudience.ALL_ATHLETES;

    @Column(nullable = false)
    @Builder.Default
    private boolean published = false;

    @Column(nullable = false)
    @Builder.Default
    private Integer version = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    @OneToMany(mappedBy = "program", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @Builder.Default
    private List<GettingStartedProgramDay> days = new ArrayList<>();
}
