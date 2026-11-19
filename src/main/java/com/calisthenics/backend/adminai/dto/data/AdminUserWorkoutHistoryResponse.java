package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserWorkoutHistoryResponse {
    private AdminTimeRangeMetadata timeRange;
    private Long userId;
    private String fullName;
    private long totalSessions;
    private long completedSessions;
    private long inProgressSessions;
    private long scheduledSessions;
    private long cancelledSessions;
    private double completionRate;
    private long totalTrainingMinutes;
    private double averageDurationMinutes;
    private double averageSessionsPerWeek;
    private String consistencyTrend;
    private long completedExercisesCount;
    private long pendingOrIncompleteExercisesCount;
    @Builder.Default
    private List<AssignedPlanItem> assignedPlans = new ArrayList<>();
    @Builder.Default
    private List<PersonalPlanItem> personalPlans = new ArrayList<>();
    @Builder.Default
    private List<WorkoutSessionItem> recentSessions = new ArrayList<>();
    @Builder.Default
    private List<WorkoutSessionItem> abandonedOrIncompleteWorkouts = new ArrayList<>();
    @Builder.Default
    private List<String> missingDataNotes = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignedPlanItem {
        private Long assignmentId;
        private Long workoutPlanId;
        private String planName;
        private String splitType;
        private String coachName;
        private String status;
        private LocalDate startDate;
        private LocalDate endDate;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalPlanItem {
        private Long workoutPlanId;
        private String name;
        private String splitType;
        private boolean active;
        private boolean aiGenerated;
        private ZonedDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WorkoutSessionItem {
        private Long sessionId;
        private String title;
        private String status;
        private String source;
        private LocalDate workoutDate;
        private Long durationSeconds;
        private long completedExercises;
        private long totalExercises;
        private String workoutPlanName;
        private ZonedDateTime startedAt;
        private ZonedDateTime completedAt;
    }
}
