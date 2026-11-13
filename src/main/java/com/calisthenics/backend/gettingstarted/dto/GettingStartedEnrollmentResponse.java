package com.calisthenics.backend.gettingstarted.dto;

import lombok.*;

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
    private Integer programVersion;
    private String status;
    private ZonedDateTime enrolledAt;
    private ZonedDateTime completedAt;
    @Builder.Default
    private List<Integer> completedDays = new ArrayList<>();
    private int completedDaysCount;
    private boolean isCompleted;
}
