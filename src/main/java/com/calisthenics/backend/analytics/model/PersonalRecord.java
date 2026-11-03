package com.calisthenics.backend.analytics.model;

import com.calisthenics.backend.exercise.Exercise;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.workout.WorkoutSession;
import com.calisthenics.backend.workout.WorkoutSessionSet;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(
        name = "personal_records",
        indexes = {
                @Index(name = "idx_prs_user_exercise", columnList = "user_id, exercise_id"),
                @Index(name = "idx_prs_user_achieved", columnList = "user_id, achieved_at"),
                @Index(name = "idx_prs_user_type", columnList = "user_id, record_type"),
                @Index(name = "idx_prs_session", columnList = "workout_session_id")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exercise_id")
    private Exercise exercise;

    @Column(name = "custom_exercise_name")
    private String customExerciseName;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_id")
    private WorkoutSession workoutSession;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workout_session_set_id")
    private WorkoutSessionSet workoutSessionSet;

    @Enumerated(EnumType.STRING)
    @Column(name = "record_type", nullable = false, length = 32)
    private PersonalRecordType recordType;

    @Column(name = "record_value", nullable = false, precision = 10, scale = 2)
    private BigDecimal recordValue;

    @Column(name = "unit", length = 32)
    private String unit;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "secondary_value", precision = 10, scale = 2)
    private BigDecimal secondaryValue;

    @Column(name = "previous_record_value", precision = 10, scale = 2)
    private BigDecimal previousRecordValue;

    @Column(name = "previous_secondary_value", precision = 10, scale = 2)
    private BigDecimal previousSecondaryValue;

    @Column(name = "improvement_percentage", precision = 6, scale = 2)
    private BigDecimal improvementPercentage;

    @Column(name = "achieved_at", nullable = false)
    private ZonedDateTime achievedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private ZonedDateTime updatedAt;

    public String getEffectiveExerciseName() {
        if (exercise != null && exercise.getName() != null && !exercise.getName().isBlank()) {
            return exercise.getName();
        }
        return (customExerciseName != null && !customExerciseName.isBlank()) ? customExerciseName : "Unknown Exercise";
    }
}
