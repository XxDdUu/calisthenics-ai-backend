package com.calisthenics.backend.adminai.dto.data;

import com.calisthenics.backend.exercise.ExerciseCategory;
import com.calisthenics.backend.exercise.ExerciseTrainingType;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkExerciseGenerationRequest {
    private String prompt;
    @Min(value = 1, message = "Quantity must be at least 1")
    private Integer quantity;
    private ExerciseTrainingType trainingType;
    private List<ExerciseCategory> categories;
    private String focus;
    private Integer batchSize;
    private Long conversationId;
    private String idempotencyKey;
}
