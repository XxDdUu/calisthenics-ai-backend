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
public class AdminToolExecutionRecord {
    private String toolName;
    private String status;
    private String parametersSummary;
    private String summary;
    private Long recordCount;
    private Long durationMs;
    private ZonedDateTime executedAt;
}
