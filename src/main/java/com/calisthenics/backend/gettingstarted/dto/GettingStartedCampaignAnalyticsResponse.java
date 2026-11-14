package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GettingStartedCampaignAnalyticsResponse {
    private Long programId;
    private String programTitle;
    private boolean published;
    private Integer version;
    private TargetAudience targetAudience;
    private String audienceLabel;
    private long eligibleAthleteCount;
    private long modalImpressions;
    private long modalDismissals;
    private long programDetailViews;
    private long enrollments;
    private long day1Completions;
    private long day2Completions;
    private long day3Completions;
    private double day1CompletionRate;
    private double day2CompletionRate;
    private double day3CompletionRate;
    private long completedEnrollments;
    private double overallCompletionRate;
}
