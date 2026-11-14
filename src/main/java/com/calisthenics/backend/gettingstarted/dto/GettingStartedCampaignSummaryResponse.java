package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GettingStartedCampaignSummaryResponse {
    private Long programId;
    private String title;
    private String description;
    private boolean published;
    private Integer version;
    private TargetAudience targetAudience;
    private String audienceLabel;
    private long eligibleAthleteCount;
    private long enrollments;
    private ZonedDateTime publishedAt;
    private ZonedDateTime updatedAt;
    private List<GettingStartedDayResponse> days;
}
