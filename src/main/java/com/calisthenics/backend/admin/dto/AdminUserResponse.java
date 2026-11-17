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
        ZonedDateTime createdAt,
        String membershipTier,
        String membershipSource,
        boolean unlimited,
        boolean paidPro,
        boolean adminGrantedPro,
        String adminProStatus,
        String adminProGrantSource,
        Long grantedByAdminId,
        ZonedDateTime adminProGrantedAt,
        Long revokedByAdminId,
        ZonedDateTime adminProRevokedAt,
        String adminProReason
) {
    public AdminUserResponse(
            Long id,
            String email,
            String fullName,
            String role,
            Gender gender,
            boolean hasCoachProfile,
            boolean hasClientProfile,
            ZonedDateTime createdAt
    ) {
        this(
                id,
                email,
                fullName,
                role,
                gender,
                hasCoachProfile,
                hasClientProfile,
                createdAt,
                "ROLE_ADMIN".equals(role) ? "ADMIN" : "FREE",
                "ROLE_ADMIN".equals(role) ? "ADMIN Role" : "Default",
                "ROLE_ADMIN".equals(role),
                false,
                false,
                "NONE",
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
