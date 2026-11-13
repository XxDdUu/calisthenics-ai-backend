package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.adminai.type.AdminAnalyticsRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminToolExecuteRequest {
    private String toolName;
    private Long userId;
    private String skillKeyword;
    private AdminUserSegmentFilter filter;
    private AdminAnalyticsRange range;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer page;
    private Integer size;
    private Boolean includeEmail;
    private String goal;
}
