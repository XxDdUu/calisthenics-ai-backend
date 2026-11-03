package com.calisthenics.backend.workout;

import com.calisthenics.backend.exercise.Exercise;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workout_session_exercises", uniqueConstraints = {
        @UniqueConstraint(name = "uk_workout_session_exercise_order",
                columnNames = {"workout_session_id", "order_index"})
}, indexes = @Index(name = "idx_session_exercises_exercise", columnList = "exercise_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutSessionExercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    @Column(nullable = false)
    @Builder.Default
    private Long version = 0L;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_session_id", nullable = false)
    private WorkoutSession workoutSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(name = "exercise_name_snapshot", nullable = false)
    private String exerciseNameSnapshot;

    @Column(name = "instructions_snapshot", columnDefinition = "TEXT")
    private String instructionsSnapshot;

    @Column(name = "plan_notes_snapshot", columnDefinition = "TEXT")
    private String planNotesSnapshot;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    @Column(name = "target_sets", nullable = false)
    private Integer targetSets;

    @Column(name = "target_reps")
    private Integer targetReps;

    @Column(name = "target_hold_duration_seconds")
    private Integer targetHoldDurationSeconds;

    @Column(name = "rest_seconds", nullable = false)
    private Integer restSeconds;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkoutSessionExerciseStatus status = WorkoutSessionExerciseStatus.IN_PROGRESS;

    @OneToMany(mappedBy = "workoutSessionExercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("setNumber ASC")
    @Builder.Default
    private List<WorkoutSessionSet> sets = new ArrayList<>();
}
