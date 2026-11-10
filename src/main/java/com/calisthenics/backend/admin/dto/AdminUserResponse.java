package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.user.Gender;
import java.time.ZonedDateTime;

public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        Gender gender,
        boolean hasCoachProfile,
        boolean hasClientProfile,
        ZonedDateTime createdAt
) {
}
