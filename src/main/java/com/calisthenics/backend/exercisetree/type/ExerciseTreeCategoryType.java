package com.calisthenics.backend.exercisetree.type;

public enum ExerciseTreeCategoryType {
    CALISTHENICS,
    GYM;

    public static ExerciseTreeCategoryType fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            return CALISTHENICS;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return ExerciseTreeCategoryType.valueOf(normalized);
    }
}
