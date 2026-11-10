package com.calisthenics.backend.user.dto;

import com.calisthenics.backend.user.Gender;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String fullName;
    private String avatarUrl;
    private String experienceLevel;
    private Gender gender;
}
