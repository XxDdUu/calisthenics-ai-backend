package com.calisthenics.backend.aichat.dto.preview;

import com.calisthenics.backend.aichat.type.WorkoutPlanPreviewStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkoutPlanChangePreviewResponse {
    private String previewId;
    private Long conversationId;
    private Long workoutPlanId;
    private String workoutPlanName;
    private WorkoutPlanPreviewStatus status;
    private Integer revision;
    private String explanation;
    private String supersedesPreviewId;
    @Builder.Default
    private List<WorkoutPlanChangeOperation> operations = new ArrayList<>();
    @Builder.Default
    private List<WorkoutPlanPreviewDayDiff> affectedDays = new ArrayList<>();
    private ZonedDateTime createdAt;
    private ZonedDateTime expiresAt;
    private ZonedDateTime resolvedAt;
    private String failureReason;
    private Boolean stale;
}
