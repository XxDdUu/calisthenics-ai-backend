package com.calisthenics.backend.adminai.dto;

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
public class WorkoutProgramRecommendationPayload {
    private String programName;
    private String targetAudience;
    private String trainingLevel;
    private int weeklyFrequency;
    private int durationWeeks;
    private String splitType;
    private String progressionCriteria;
    private String requiredEquipment;
    private String safetyConsiderations;
    private String rationale;
    @Builder.Default
    private List<ProgramDayDto> days = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramDayDto {
        private String dayOfWeek;
        private String title;
        private boolean restDay;
        @Builder.Default
        private List<ProgramExerciseDto> exercises = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ProgramExerciseDto {
        private Long exerciseId;
        private String exerciseName;
        private int sets;
        private Integer reps;
        private Integer holdSeconds;
        private int restSeconds;
        private String notes;
    }
}
