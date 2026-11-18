package com.calisthenics.backend.adminai.dto.data;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkExerciseGenerationJobResponse {
    private Long id;
    private String jobUid;
    private Long conversationId;
    private String prompt;
    private String trainingTypeFilter;
    private String categoriesFilter;
    private int requestedQuantity;
    private int batchSize;
    private int generatedCount;
    private int duplicateCount;
    private int savedCount;
    private int failedCount;
    private int processedBatches;
    private int totalBatchesEstimated;
    private int progressPercent;
    private String status;
    private String errorMessage;
    private boolean summaryPosted;
    private boolean canResume;
    private String statusMessage;
    private List<String> skippedDuplicateSamples;
    private ZonedDateTime createdAt;
    private ZonedDateTime startedAt;
    private ZonedDateTime completedAt;
    private ZonedDateTime updatedAt;
}
