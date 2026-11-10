package com.calisthenics.backend.aiworkout.dto;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.calisthenics.backend.workoutplan.type.WorkoutSplitType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiWorkoutPlanPreviewResponse {

    private String planName;
    private String description;
    private Integer durationWeeks;
    private LocalDate startDate;
    private WorkoutSplitType splitType;
    private List<AiWorkoutSessionPreview> sessions;
    private AiWorkoutMetadata metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiWorkoutSessionPreview {
        private WorkoutDayOfWeek dayOfWeek;
        private String title;
        private boolean restDay;
        private List<AiWorkoutExercisePreview> exercises;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiWorkoutExercisePreview {
        private Long exerciseId;
        private String exerciseName;
        private String exerciseCode;
        private String category;
        private Integer sets;
        private Integer reps;
        private Integer holdSeconds;
        private Integer restSeconds;
        private String notes;
        private boolean requiresReview;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AiWorkoutMetadata {
        private String modelUsed;
        private Integer inputTokens;
        private Integer outputTokens;
        private Long latencyMs;
        private boolean hasConflicts;
        private String existingActivePlanName;
    }
}
