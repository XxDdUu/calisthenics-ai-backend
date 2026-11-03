package com.calisthenics.backend.workout;

import com.calisthenics.backend.user.User;
import com.calisthenics.backend.coachclient.entity.WorkoutPlanAssignment;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlan;
import com.calisthenics.backend.workoutplan.entity.WorkoutPlanDay;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "workout_sessions", indexes = {
        @Index(name = "idx_workout_sessions_user_status", columnList = "user_id, status"),
        @Index(name = "idx_workout_sessions_user_date", columnList = "user_id, workout_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_plan_id")
    private WorkoutPlan workoutPlan;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_plan_day_id")
    private WorkoutPlanDay workoutPlanDay;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_plan_assignment_id")
    private WorkoutPlanAssignment workoutPlanAssignment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkoutSessionSource source = WorkoutSessionSource.LEGACY;

    @Column(name = "workout_date")
    private LocalDate workoutDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkoutSessionStatus status = WorkoutSessionStatus.COMPLETED;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "last_saved_at")
    private ZonedDateTime lastSavedAt;

    @Column(name = "duration_seconds")
    private Long durationSeconds;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    @OneToMany(mappedBy = "workoutSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<WorkoutSessionExercise> exercises = new ArrayList<>();

    @OneToMany(mappedBy = "workoutSession", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<WorkoutLog> logs = new ArrayList<>();
}
