package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.admin.dto.AdminExerciseResponse;
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
public class ExerciseDraftBatchResultResponse {
    private boolean success;
    private String errorMessage;
    private String category;
    private int requestedCount;
    private int createdCount;
    @Builder.Default
    private List<AdminExerciseResponse> createdExercises = new ArrayList<>();
    @Builder.Default
    private List<AdminExerciseResponse> existingDrafts = new ArrayList<>();
    private String message;
}
