package com.calisthenics.backend.user.dto;

import lombok.Data;

@Data
public class UpdateProfileRequest {

    private String fullName;
    private String avatarUrl;
    private String experienceLevel;
}
