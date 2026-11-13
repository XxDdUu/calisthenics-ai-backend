package com.calisthenics.backend.gettingstarted.dto;

import lombok.*;

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
    private boolean completed;
}
