package com.calisthenics.backend.gettingstarted.entity;

import com.calisthenics.backend.exercise.Exercise;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(
        name = "getting_started_program_exercises",
        uniqueConstraints = @UniqueConstraint(name = "uk_getting_started_exercise_order", columnNames = {"program_day_id", "display_order"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedProgramExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_day_id", nullable = false)
    private GettingStartedProgramDay programDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(name = "exercise_name", nullable = false, length = 150)
    private String exerciseName;

    @Column(nullable = false)
    @Builder.Default
    private Integer sets = 3;

    @Column
    private Integer reps;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(name = "rest_seconds", nullable = false)
    @Builder.Default
    private Integer restSeconds = 60;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 0;
}
