package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserSegmentsResponse {
    private AdminTimeRangeMetadata timeRange;
    private AdminUserSegmentFilter appliedFilter;
    private String segmentSummary;
    private long matchedCount;
    private int returnedCount;
    private int page;
    private int size;
    private int totalPages;
    private List<AdminGlobalUserSummaryDto> users;
    private List<BucketCountDto> experienceBreakdown;
    private List<BucketCountDto> activityBreakdown;
    private List<BucketCountDto> goalBreakdown;
    private List<BucketCountDto> coachStatusBreakdown;
    private List<String> missingDataNotes;
}
