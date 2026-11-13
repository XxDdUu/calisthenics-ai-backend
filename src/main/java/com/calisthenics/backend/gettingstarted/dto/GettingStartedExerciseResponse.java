package com.calisthenics.backend.gettingstarted.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedExerciseResponse {
    private Long id;
    private Long exerciseId;
    private String exerciseName;
    private Integer sets;
    private Integer reps;
    private Integer durationSeconds;
    private Integer restSeconds;
    private String instructions;
    private Integer displayOrder;
}
