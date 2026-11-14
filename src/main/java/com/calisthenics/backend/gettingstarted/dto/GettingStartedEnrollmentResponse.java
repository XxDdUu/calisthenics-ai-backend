package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.GettingStartedEnrollmentStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedEnrollmentResponse {
    private Long id;
    private Long programId;
    private String programTitle;
    private String programDescription;
    private Integer programVersion;
    private GettingStartedEnrollmentStatus status;
    private boolean addedToWorkoutPlan;
    private LocalDate startDate;
    private ZonedDateTime enrolledAt;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    @Builder.Default
    private List<Integer> completedDays = new ArrayList<>();
    @Builder.Default
    private List<GettingStartedDayProgressResponse> dayProgress = new ArrayList<>();
    private int completedDaysCount;
    private int totalDays;
    @com.fasterxml.jackson.annotation.JsonProperty("isCompleted")
    private boolean isCompleted;
}
