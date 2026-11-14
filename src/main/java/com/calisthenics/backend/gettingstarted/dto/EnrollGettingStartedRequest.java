package com.calisthenics.backend.gettingstarted.dto;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EnrollGettingStartedRequest {
    private Boolean addToWorkoutPlan;
    private Boolean confirmConflicts;
    private LocalDate startDate;
}
