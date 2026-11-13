package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailDossierResponse {
    private AdminTimeRangeMetadata timeRange;
    private Long userId;
    private String fullName;
    private String displayName;
    private String maskedEmail;
    private String email;
    private String role;
    private String accountCategory;
    private String gender;
    private String bio;
    private String experienceLevel;
    private String trainingExperience;
    private String primaryGoals;
    private String trainingInterests;
    private String preferredFrequency;
    private String coachingLanguage;
    private BigDecimal heightCm;
    private BigDecimal latestWeightKg;
    private BigDecimal latestBodyFatPercentage;
    private ZonedDateTime latestMeasurementAt;
    private List<String> inferredAvailableEquipment;
    private ZonedDateTime registeredAt;
    private CoachProfileSummary coachProfile;
    private AdminUserWorkoutHistoryResponse workoutSummary;
    private AdminUserPersonalRecordsResponse personalRecordsSummary;
    private AdminUserProgressResponse progressSummary;
    private AdminUserActivityResponse activitySummary;
    private List<CoachRelationshipItem> coachRelationships;
    private List<String> missingDataFields;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoachProfileSummary {
        private String displayName;
        private String specialties;
        private Integer yearsExperience;
        private String languages;
        private Boolean isPublic;
        private Boolean isAcceptingClients;
        private Boolean isVerified;
        private long activeClientsCount;
        private long pendingInvitationsCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CoachRelationshipItem {
        private Long relationshipId;
        private String counterpartRole;
        private Long counterpartUserId;
        private String counterpartName;
        private String status;
        private String requestedByRole;
        private ZonedDateTime createdAt;
        private ZonedDateTime updatedAt;
    }
}
