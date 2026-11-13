package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AdminGettingStartedProgramRequest {

    @NotBlank(message = "Program title is required")
    private String title;

    private String description;

    @Builder.Default
    private TargetAudience targetAudience = TargetAudience.ALL_ATHLETES;

    @NotEmpty(message = "Program must define days")
    @Size(min = 3, max = 3, message = "Getting Started program must have exactly 3 days")
    @Valid
    @Builder.Default
    private List<AdminGettingStartedDayRequest> days = new ArrayList<>();
}
