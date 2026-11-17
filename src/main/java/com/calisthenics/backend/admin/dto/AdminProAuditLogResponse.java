package com.calisthenics.backend.admin.dto;

import java.time.ZonedDateTime;

public record AdminProAuditLogResponse(
        Long id,
        Long targetUserId,
        Long adminUserId,
        String action,
        String grantSource,
        String previousEffectiveTier,
        String newEffectiveTier,
        String previousStatus,
        String newStatus,
        String reason,
        ZonedDateTime createdAt
) {
}
