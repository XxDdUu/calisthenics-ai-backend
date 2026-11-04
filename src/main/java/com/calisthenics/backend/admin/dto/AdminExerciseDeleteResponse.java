package com.calisthenics.backend.admin.dto;

public record AdminExerciseDeleteResponse(
        boolean success,
        boolean softDeleted,
        String message
) {
}
