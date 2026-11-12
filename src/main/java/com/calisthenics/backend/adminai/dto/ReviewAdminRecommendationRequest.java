package com.calisthenics.backend.adminai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewAdminRecommendationRequest {
    private String reviewNotes;
    private Boolean publishExercise;
}
