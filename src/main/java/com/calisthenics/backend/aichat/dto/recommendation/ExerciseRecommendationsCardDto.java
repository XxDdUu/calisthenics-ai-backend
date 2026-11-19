package com.calisthenics.backend.aichat.dto.recommendation;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExerciseRecommendationsCardDto {

    @Builder.Default
    private String type = "EXERCISE_RECOMMENDATIONS";
    @Builder.Default
    private boolean readOnly = true;
    @Builder.Default
    private boolean mutated = false;

    private String focusLabel;
    private WorkoutDayOfWeek suggestedDayOfWeek;
    private String suggestedDayLabel;
    private Integer targetDayExerciseCount;
    private Integer targetDayTotalSets;

    @Builder.Default
    private List<MuscleGapInsightDto> undertrainedMuscles = new ArrayList<>();
    @Builder.Default
    private List<PlanDayOptionDto> availablePlanDays = new ArrayList<>();
    @Builder.Default
    private List<String> planWarnings = new ArrayList<>();
    @Builder.Default
    private List<ExerciseRecommendationItemDto> recommendations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MuscleGapInsightDto {
        private String muscleId;
        private String displayName;
        private Double weeklySets;
        private Double daySets;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanDayOptionDto {
        private WorkoutDayOfWeek dayOfWeek;
        private String dayTitle;
        private String sessionType;
        private boolean restDay;
        private int exerciseCount;
    }
}
