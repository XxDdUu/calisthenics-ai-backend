package com.calisthenics.backend.workoutplan.entity;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSessionType;
import jakarta.persistence.*;
import lombok.*;

import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(
        name = "workout_plan_days",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_workout_plan_day_weekday", columnNames = {"workout_plan_id", "day_of_week"}),
                @UniqueConstraint(name = "uk_workout_plan_day_display_order", columnNames = {"workout_plan_id", "display_order"})
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WorkoutPlanDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workout_plan_id", nullable = false)
    private WorkoutPlan workoutPlan;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false, length = 10)
    private WorkoutDayOfWeek dayOfWeek;

    /** Optional predefined category; the user-facing workout name stays free-form. */
    @Enumerated(EnumType.STRING)
    @Column(name = "session_type", length = 30)
    private WorkoutSessionType sessionType;

    /** Legacy database column reused as the free-form custom workout name. */
    @Column(name = "name", length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_rest_day", nullable = false)
    @Builder.Default
    private boolean restDay = false;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @OneToMany(mappedBy = "workoutPlanDay", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    @Builder.Default
    private Set<WorkoutPlanExercise> exercises = new LinkedHashSet<>();

    /** Keeps programmatic/legacy entity creation compatible with the seven-day schema. */
    @PrePersist
    void applyDefaults() {
        if (displayOrder == null && dayOfWeek != null) {
            displayOrder = dayOfWeek.getOrder();
        }
        if (sessionType == WorkoutSessionType.REST) {
            restDay = true;
        }
    }
}
