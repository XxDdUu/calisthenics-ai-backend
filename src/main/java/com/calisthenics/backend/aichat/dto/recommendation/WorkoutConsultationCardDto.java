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
public class WorkoutConsultationCardDto {

    @Builder.Default
    private String type = "WORKOUT_CONSULTATION";
    @Builder.Default
    private boolean readOnly = true;
    @Builder.Default
    private boolean mutated = false;

    private String focusArea;
    private WorkoutDayOfWeek focusDayOfWeek;
    private Integer balanceScore;

    @Builder.Default
    private List<ConsultationFindingDto> findings = new ArrayList<>();
    @Builder.Default
    private List<String> undertrainedMuscles = new ArrayList<>();
    @Builder.Default
    private List<String> overloadedMuscles = new ArrayList<>();
    @Builder.Default
    private List<String> adequatelyCoveredMuscles = new ArrayList<>();
    @Builder.Default
    private List<String> programPriorities = new ArrayList<>();
    private Boolean specializedProgram;
    private AthleteSnapshotDto athleteSnapshot;
    @Builder.Default
    private List<MuscleCoverageDto> muscleCoverage = new ArrayList<>();
    @Builder.Default
    private List<MovementPatternAuditDto> movementPatterns = new ArrayList<>();
    private String suggestedActionPrompt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ConsultationFindingDto {
        private String severity; // "INFO", "WARNING"
        private String title;
        private String detail;
        private Integer priorityTier;
        private String coverageStatus; // "MISSING", "UNDERDEVELOPED", "ADEQUATELY_COVERED", "OVERLOADED"
        private WorkoutDayOfWeek recommendedDay;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AthleteSnapshotDto {
        private Double weightKg;
        private Double heightCm;
        private String experienceLevel;
        private String primaryGoals;
        private String preferredFrequency;
        private Integer completedSessionsCount;
        private Integer incompleteSessionsCount;
        @Builder.Default
        private List<String> keyPrs = new ArrayList<>();
        @Builder.Default
        private List<String> missingData = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MuscleCoverageDto {
        private String muscleName;
        private Double directSets;
        private Double indirectSets;
        private Double effectiveSets;
        private String status;
        @Builder.Default
        private List<String> contributingExercises = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MovementPatternAuditDto {
        private String patternName;
        private Double weeklySets;
        private Integer exerciseCount;
        private String status;
        @Builder.Default
        private List<String> existingExercises = new ArrayList<>();
    }
}
