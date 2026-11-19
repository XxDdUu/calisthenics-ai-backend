package com.calisthenics.backend.exercise.muscle;

import com.calisthenics.backend.exercise.Exercise;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;

@Entity
@Table(
        name = "exercise_muscle_targets",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_exercise_muscle_target",
                columnNames = {"exercise_id", "muscle_id"}
        ),
        indexes = {
                @Index(name = "idx_exercise_muscle_targets_exercise", columnList = "exercise_id"),
                @Index(name = "idx_exercise_muscle_targets_muscle", columnList = "muscle_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExerciseMuscleTarget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "exercise_id", nullable = false)
    private Exercise exercise;

    @Column(name = "muscle_id", nullable = false, length = 64)
    private String muscleId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private MuscleRole role;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private int displayOrder = 0;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;
}
