package com.calisthenics.backend.profile.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClientProfileRequest {

    @Size(max = 100, message = "Display name must not exceed 100 characters")
    private String displayName;

    @Size(max = 500, message = "Avatar URL must not exceed 500 characters")
    private String avatarUrl;

    @Size(max = 500, message = "Bio must not exceed 500 characters")
    private String bio;

    @Size(max = 50, message = "Experience level must not exceed 50 characters")
    private String experienceLevel;

    @Size(max = 500, message = "Training experience must not exceed 500 characters")
    private String trainingExperience;

    @Size(max = 500, message = "Primary goals must not exceed 500 characters")
    private String primaryGoals;

    @Size(max = 500, message = "Training interests must not exceed 500 characters")
    private String trainingInterests;

    @Size(max = 50, message = "Preferred frequency must not exceed 50 characters")
    private String preferredFrequency;

    @Size(max = 100, message = "Coaching language must not exceed 100 characters")
    private String coachingLanguage;
}
