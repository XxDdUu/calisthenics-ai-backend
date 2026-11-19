package com.calisthenics.backend.aiworkout.gemini.dto;

import com.calisthenics.backend.adminai.dto.data.ExerciseDraftCandidateDto;

import java.util.Collections;
import java.util.List;

/**
 * Structured outcome and telemetry from a dedicated Gemini exercise-draft batch generation call.
 */
public record GeminiExerciseBatchOutcome(
        Status status,
        List<ExerciseDraftCandidateDto> drafts,
        int rawResponseLength,
        String finishReason,
        String errorDetail,
        String modelUsed,
        long latencyMs,
        Integer outputTokens
) {
    public enum Status {
        OK,
        NOT_CONFIGURED,
        API_TIMEOUT,
        MAX_TOKENS_TRUNCATED,
        MALFORMED_JSON,
        HTTP_ERROR,
        EMPTY_RESPONSE,
        UNEXPECTED_ERROR
    }

    public GeminiExerciseBatchOutcome {
        drafts = drafts == null ? Collections.emptyList() : List.copyOf(drafts);
    }

    public static GeminiExerciseBatchOutcome ok(
            List<ExerciseDraftCandidateDto> drafts,
            int rawResponseLength,
            String finishReason,
            String modelUsed,
            long latencyMs,
            Integer outputTokens
    ) {
        return new GeminiExerciseBatchOutcome(
                Status.OK,
                drafts,
                rawResponseLength,
                finishReason,
                null,
                modelUsed,
                latencyMs,
                outputTokens
        );
    }

    public static GeminiExerciseBatchOutcome failed(
            Status status,
            int rawResponseLength,
            String finishReason,
            String errorDetail,
            String modelUsed,
            long latencyMs,
            Integer outputTokens
    ) {
        return new GeminiExerciseBatchOutcome(
                status,
                Collections.emptyList(),
                rawResponseLength,
                finishReason,
                errorDetail,
                modelUsed,
                latencyMs,
                outputTokens
        );
    }

    public boolean isTruncated() {
        return status == Status.MAX_TOKENS_TRUNCATED || "MAX_TOKENS".equalsIgnoreCase(finishReason);
    }
}
