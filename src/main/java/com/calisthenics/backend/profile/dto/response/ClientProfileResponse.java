package com.calisthenics.backend.profile.dto.response;

import com.calisthenics.backend.user.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileResponse {

    private Long id; // Client Profile ID or User ID
    private Long userId;
    private String fullName;
    private String displayName;
    private String avatarUrl;
    private String bio;
    private String experienceLevel;
    private Gender gender;
    private String trainingExperience;
    private String primaryGoals;
    private String trainingInterests;
    private String preferredFrequency;
    private String coachingLanguage;
    private Double heightCm;
    private Double weightKg;

    // Training overview details
    private Long activePlansCount;
    private Long completedSessionsCount;
    private Long currentPlanId;
    private String currentPlanName;

    // Connected coach details (if any)
    private ConnectedCoachSummary connectedCoach;

    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
