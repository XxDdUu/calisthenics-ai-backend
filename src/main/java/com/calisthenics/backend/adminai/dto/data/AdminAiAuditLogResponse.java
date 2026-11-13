package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminAiAuditLogResponse {
    private Long id;
    private Long adminUserId;
    private String action;
    private String targetType;
    private Long targetId;
    private String details;
    private ZonedDateTime createdAt;
}
