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
public class BulkExerciseGenerationItemResponse {
    private Long id;
    private Integer sequenceNumber;
    private Long exerciseId;
    private String exerciseName;
    private String exerciseCode;
    private String trainingType;
    private String category;
    private String difficulty;
    private String outcome;
    private String reason;
    private int batchNumber;
    private ZonedDateTime createdAt;
}
