package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExerciseDraftBatchResultResponse {
    private boolean success;
    private boolean validationFailed;
    private String errorMessage;
    private String failureReasonCode;
    private String category;
    private int requestedCount;
    private int createdCount;
    private int validCount;
    private int duplicateCount;
    private int rejectedCount;
    private int retryCount;
    @Builder.Default
    private List<String> validationErrors = new ArrayList<>();
    @Builder.Default
    private List<AdminExerciseResponse> createdExercises = new ArrayList<>();
    @Builder.Default
    private List<AdminExerciseResponse> existingDrafts = new ArrayList<>();
    private String message;

    public ExerciseDraftBatchResultResponse normalizeCollections() {
        if (validationErrors == null) validationErrors = Collections.emptyList();
        if (createdExercises == null) createdExercises = Collections.emptyList();
        if (existingDrafts == null) existingDrafts = Collections.emptyList();
        return this;
    }
}
