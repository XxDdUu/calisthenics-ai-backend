package com.calisthenics.backend.gettingstarted.dto;

import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedEnrollPreviewResponse {
    private Long programId;
    private String programTitle;
    private boolean alreadyEnrolled;
    private boolean alreadyAddedToWorkoutPlan;
    private LocalDate startDate;
    @Builder.Default
    private List<LocalDate> plannedDates = new ArrayList<>();
    private boolean hasConflicts;
    @Builder.Default
    private List<GettingStartedScheduleConflictResponse> conflicts = new ArrayList<>();
}
