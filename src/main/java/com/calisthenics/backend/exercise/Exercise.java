package com.calisthenics.backend.exercise;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "exercises")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Exercise {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExerciseCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DifficultyLevel difficulty;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_muscles", columnDefinition = "TEXT")
    private String targetMuscles;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    @Column(name = "image_url")
    private String imageUrl;

    @Column(name = "video_url")
    private String videoUrl;

    @Column(name = "equipment")
    private String equipment;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExerciseStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    @Builder.Default
    private ExerciseOrigin origin = ExerciseOrigin.MANUAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "training_type", nullable = false, length = 32)
    @Builder.Default
    private ExerciseTrainingType trainingType = ExerciseTrainingType.CALISTHENICS;

    @Column(name = "secondary_muscles", columnDefinition = "TEXT")
    private String secondaryMuscles;

    @Column(name = "exercise_type", length = 32)
    private String exerciseType;

    @Column(name = "recommended_sets")
    private Integer recommendedSets;

    @Column(name = "recommended_reps", length = 64)
    private String recommendedReps;

    @Column(name = "recommended_rest_seconds")
    private Integer recommendedRestSeconds;

    @Column(name = "common_mistakes", columnDefinition = "TEXT")
    private String commonMistakes;

    @Column(name = "safety_considerations", columnDefinition = "TEXT")
    private String safetyConsiderations;

    @Column(name = "progression_notes", columnDefinition = "TEXT")
    private String progressionNotes;

    @Column(name = "aliases", columnDefinition = "TEXT")
    private String aliases;

    @OneToMany(mappedBy = "exercise", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC, id ASC")
    @Builder.Default
    private List<ExerciseMediaItem> mediaItems = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private ZonedDateTime createdAt;

    public ExerciseStatus getStatus() {
        return status != null ? status : (active ? ExerciseStatus.PUBLISHED : ExerciseStatus.DRAFT);
    }

    public ExerciseOrigin getOrigin() {
        return origin != null ? origin : ExerciseOrigin.MANUAL;
    }

    public ExerciseTrainingType getTrainingType() {
        return trainingType != null ? trainingType : ExerciseTrainingType.CALISTHENICS;
    }

    public void setActive(boolean active) {
        this.active = active;
        this.status = active ? ExerciseStatus.PUBLISHED : ExerciseStatus.DRAFT;
    }

    public void setStatus(ExerciseStatus status) {
        this.status = status;
        if (status != null) {
            this.active = (status == ExerciseStatus.PUBLISHED);
        }
    }

    @PrePersist
    @PreUpdate
    protected void syncLifecycleFields() {
        if (origin == null) {
            origin = ExerciseOrigin.MANUAL;
        }
        if (trainingType == null) {
            trainingType = ExerciseTrainingType.CALISTHENICS;
        }
        if (status == null) {
            status = active ? ExerciseStatus.PUBLISHED : ExerciseStatus.DRAFT;
        } else {
            active = (status == ExerciseStatus.PUBLISHED);
        }
    }

    public static class ExerciseBuilder {
        public ExerciseBuilder status(ExerciseStatus status) {
            this.status = status;
            if (status != null) {
                this.active(status == ExerciseStatus.PUBLISHED);
            }
            return this;
        }
    }
}
