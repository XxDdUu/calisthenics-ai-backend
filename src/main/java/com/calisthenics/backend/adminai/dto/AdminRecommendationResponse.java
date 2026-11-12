package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.adminai.type.AdminRecommendationPriority;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminRecommendationResponse {
    private Long id;
    private AdminRecommendationType type;
    private AdminRecommendationStatus status;
    private AdminRecommendationPriority priority;
    private String title;
    private String summary;
    private String rationale;
    private ExerciseRecommendationPayload exerciseDetails;
    private WorkoutProgramRecommendationPayload programDetails;
    private OperationalRecommendationPayload operationalDetails;
    private Long conversationId;
    private Long createdByAdminId;
    private Long reviewedByAdminId;
    private String reviewNotes;
    private Long createdResourceId;
    private String createdResourceType;
    private ZonedDateTime reviewedAt;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
