package com.calisthenics.backend.user.dto;

import com.calisthenics.backend.user.Gender;
import com.calisthenics.backend.user.OnboardingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileResponse {

    private Long id;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String role;
    private String experienceLevel;
    private Gender gender;
    private OnboardingStatus onboardingStatus;
    private ZonedDateTime createdAt;
}
