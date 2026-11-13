package com.calisthenics.backend.gettingstarted.entity;

import com.calisthenics.backend.gettingstarted.enums.GettingStartedEnrollmentStatus;
import com.calisthenics.backend.user.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
        name = "getting_started_enrollments",
        uniqueConstraints = @UniqueConstraint(name = "uk_getting_started_user_program", columnNames = {"user_id", "program_id"}),
        indexes = @Index(name = "idx_getting_started_enrollments_user", columnList = "user_id")
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedEnrollment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private GettingStartedProgram program;

    @Column(name = "program_version", nullable = false)
    private Integer programVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private GettingStartedEnrollmentStatus status = GettingStartedEnrollmentStatus.ACTIVE;

    @CreationTimestamp
    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private ZonedDateTime enrolledAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @OneToMany(mappedBy = "enrollment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayNumber ASC")
    @Builder.Default
    private List<GettingStartedDayCompletion> completions = new ArrayList<>();
}
