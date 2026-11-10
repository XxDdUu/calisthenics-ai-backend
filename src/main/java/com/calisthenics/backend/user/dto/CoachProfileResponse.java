package com.calisthenics.backend.user.dto;

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
public class CoachProfileResponse {

    private Long id; // Coach / User ID
    private Long userId;
    private String fullName;
    private String displayName;
    private String avatarUrl;
    private String experienceLevel;
    private Gender gender;
    private String bio;
    private String specialties;
    private Integer yearsExperience;
    private String trainingPhilosophy;
    private String coachingDescription;
    private String languages;
    private String certifications;
    private String socialLinks;
    private Boolean isPublic;
    private Boolean isAcceptingClients;
    private Boolean isVerified;

    // Real backend statistics
    private Long activeClientsCount;
    private Long completedClientsCount;
    private Long assignedPlansCount;

    // Connection context relative to the requesting user
    private String connectionStatus; // PENDING, ACTIVE, REJECTED, CANCELLED, or null
    private Long relationshipId;
    private Long conversationId;

    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
