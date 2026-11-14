package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.GettingStartedDayStatus;
import lombok.*;

import java.time.LocalDate;
import java.time.ZonedDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedDayProgressResponse {
    private Integer dayNumber;
    private String title;
    private GettingStartedDayStatus status;
    private LocalDate scheduledDate;
    private Long workoutSessionId;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
}
