package com.calisthenics.backend.adminai.dto;

import com.calisthenics.backend.adminai.type.AdminRecommendationPriority;
import com.calisthenics.backend.adminai.type.AdminRecommendationStatus;
import com.calisthenics.backend.adminai.type.AdminRecommendationType;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAdminRecommendationRequest {
    private AdminRecommendationType type;
    private AdminRecommendationStatus status;
    private AdminRecommendationPriority priority;
    private String title;
    private String summary;
    private String rationale;
    private ExerciseTrainingType trainingType;
    private String category;
    private String targetSkill;
    private String trainingLevel;
    private String prompt;
    private ExerciseRecommendationPayload exerciseDetails;
    private WorkoutProgramRecommendationPayload programDetails;
    private OperationalRecommendationPayload operationalDetails;
}
