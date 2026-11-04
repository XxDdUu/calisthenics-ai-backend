package com.calisthenics.backend.admin.dto;

import java.time.ZonedDateTime;

public record AdminUserResponse(
        Long id,
        String email,
        String fullName,
        String role,
        boolean hasCoachProfile,
        boolean hasClientProfile,
        ZonedDateTime createdAt
) {
}
