package com.calisthenics.backend.aichat.dto.preview;

import com.calisthenics.backend.workoutplan.type.WorkoutDayOfWeek;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
public class CreateRecommendationPreviewRequest {

    @NotEmpty(message = "At least one exercise must be selected")
    @Builder.Default
    private List<Long> exerciseIds = new ArrayList<>();

    @NotNull(message = "Target weekday is required")
    private WorkoutDayOfWeek dayOfWeek;

    private Integer sets;
    private Integer reps;
    private Integer durationSeconds;
    private Integer restSeconds;
}
