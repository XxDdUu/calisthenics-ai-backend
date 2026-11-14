package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GettingStartedProgramPreviewResponse {
    private GettingStartedProgramResponse program;
    private boolean valid;
    private List<String> validationErrors;
    private TargetAudience targetAudience;
    private String audienceLabel;
    private long eligibleAthleteCount;
    private long totalAthleteCount;
    private long estimatedAudienceSize;
}
