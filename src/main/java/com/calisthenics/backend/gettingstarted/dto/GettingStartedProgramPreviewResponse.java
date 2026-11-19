package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
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
public class GettingStartedProgramPreviewResponse {
    private GettingStartedProgramResponse program;
    private boolean valid;
    @Builder.Default
    private List<String> validationErrors = new ArrayList<>();
    private TargetAudience targetAudience;
    private String audienceLabel;
    private long eligibleAthleteCount;
    private long totalAthleteCount;
    private long estimatedAudienceSize;
}
