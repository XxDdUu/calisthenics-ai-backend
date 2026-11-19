package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserPersonalRecordsResponse {
    private AdminTimeRangeMetadata timeRange;
    private Long userId;
    private String fullName;
    private long totalRecords;
    private long recordsInRange;
    private Double averageImprovementPercent;
    private Double maxImprovementPercent;
    private ZonedDateTime lastPrAchievedAt;
    private Long daysSinceLastPr;
    private long sharedPrCount;
    private long coachFeedbackCount;
    @Builder.Default
    private List<PersonalRecordItem> records = new ArrayList<>();
    @Builder.Default
    private List<SharedPrItem> sharingAndFeedbackHistory = new ArrayList<>();
    @Builder.Default
    private List<String> missingDataNotes = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PersonalRecordItem {
        private Long recordId;
        private String exerciseName;
        private String recordType;
        private BigDecimal recordValue;
        private String unit;
        private BigDecimal previousRecordValue;
        private BigDecimal improvementPercentage;
        private ZonedDateTime achievedAt;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SharedPrItem {
        private Long sharedId;
        private Long personalRecordId;
        private String exerciseName;
        private Long coachUserId;
        private String coachName;
        private String status;
        private Integer rating;
        private String coachFeedback;
        private ZonedDateTime sharedAt;
        private ZonedDateTime feedbackAt;
    }
}
