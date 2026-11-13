package com.calisthenics.backend.gettingstarted.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminGettingStartedDayRequest {

    @NotNull(message = "Day number is required")
    @Min(value = 1, message = "Day number must be 1, 2, or 3")
    @Max(value = 3, message = "Day number must be 1, 2, or 3")
    private Integer dayNumber;

    @NotBlank(message = "Day title is required")
    private String title;

    private String description;

    @NotEmpty(message = "Each day must have at least one exercise")
    @Valid
    @Builder.Default
    private List<AdminGettingStartedExerciseRequest> exercises = new ArrayList<>();
}
