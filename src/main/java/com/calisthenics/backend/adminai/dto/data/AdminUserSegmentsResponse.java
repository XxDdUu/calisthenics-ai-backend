package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
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
    @Builder.Default
    private List<AdminGlobalUserSummaryDto> users = new ArrayList<>();
    @Builder.Default
    private List<BucketCountDto> experienceBreakdown = new ArrayList<>();
    @Builder.Default
    private List<BucketCountDto> activityBreakdown = new ArrayList<>();
    @Builder.Default
    private List<BucketCountDto> goalBreakdown = new ArrayList<>();
    @Builder.Default
    private List<BucketCountDto> coachStatusBreakdown = new ArrayList<>();
    @Builder.Default
    private List<String> missingDataNotes = new ArrayList<>();
}
