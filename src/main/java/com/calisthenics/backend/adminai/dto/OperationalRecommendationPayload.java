package com.calisthenics.backend.adminai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OperationalRecommendationPayload {
    private String identifiedIssue;
    private String supportingData;
    private String suggestedAction;
    private String expectedBenefit;
    private String affectedArea;
}
