package com.calisthenics.backend.gettingstarted.entity;

import com.calisthenics.backend.workout.WorkoutSession;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "getting_started_day_completions",
        uniqueConstraints = @UniqueConstraint(name = "uk_getting_started_day_completion", columnNames = {"enrollment_id", "day_number"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedDayCompletion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "enrollment_id", nullable = false)
    private GettingStartedEnrollment enrollment;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id")
    private WorkoutSession workoutSession;

    @CreationTimestamp
    @Column(name = "completed_at", nullable = false, updatable = false)
    private ZonedDateTime completedAt;
}
