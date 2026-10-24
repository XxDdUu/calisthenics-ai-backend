package com.calisthenics.backend.workout.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.List;

@Data
public class CreateWorkoutSessionRequest {

    @NotBlank(message = "Session title is required")
    private String title;

    private String notes;

    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;

    @Valid
    private List<WorkoutLogDto> logs;
}
