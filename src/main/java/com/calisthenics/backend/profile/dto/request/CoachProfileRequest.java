package com.calisthenics.backend.profile.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachProfileRequest {

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avatarUrl;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;

    @Size(max = 500, message = "Specialties must not exceed 500 characters")
    private String specialties;

    @Min(value = 0, message = "Years of experience cannot be negative")
    private Integer yearsExperience;

    @Size(max = 1000, message = "Training philosophy must not exceed 1000 characters")
    private String trainingPhilosophy;

    @Size(max = 2000, message = "Coaching description must not exceed 2000 characters")
    private String coachingDescription;

    @Size(max = 255, message = "Languages must not exceed 255 characters")
    private String languages;

    @Size(max = 1000, message = "Certifications must not exceed 1000 characters")
    private String certifications;

    @Size(max = 1000, message = "Social links must not exceed 1000 characters")
    private String socialLinks;

    private Boolean isPublic;

    private Boolean isAcceptingClients;
}
