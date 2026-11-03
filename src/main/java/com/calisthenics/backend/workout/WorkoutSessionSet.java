package com.calisthenics.backend.workout;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "workout_session_sets", uniqueConstraints = @UniqueConstraint(
        name = "uk_session_exercise_set_number",
        columnNames = {"workout_session_exercise_id", "set_number"}
))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutSessionSet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_session_exercise_id", nullable = false)
    private WorkoutSessionExercise workoutSessionExercise;

    @Column(name = "set_number", nullable = false)
    private Integer setNumber;

    @Column
    private Integer reps;

    @Column(name = "hold_duration_seconds")
    private Integer holdDurationSeconds;

    @Column(name = "weight_kg", precision = 7, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "assistance_weight_kg", precision = 7, scale = 2)
    private BigDecimal assistanceWeightKg;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
