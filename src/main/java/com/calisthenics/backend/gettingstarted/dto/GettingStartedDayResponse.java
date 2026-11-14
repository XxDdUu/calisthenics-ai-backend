package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.GettingStartedDayStatus;
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
public class GettingStartedDayResponse {
    private Long id;
    private Integer dayNumber;
    private String title;
    private String description;
    @Builder.Default
    private List<GettingStartedExerciseResponse> exercises = new ArrayList<>();
    @Builder.Default
    private GettingStartedDayStatus status = GettingStartedDayStatus.NOT_STARTED;
    private boolean completed;
    private LocalDate scheduledDate;
    private Long workoutSessionId;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
}
