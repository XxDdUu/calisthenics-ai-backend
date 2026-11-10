package com.calisthenics.backend.aiworkout.exception;

import lombok.Getter;

import java.util.List;

@Getter
public class AiWorkoutPlanValidationException extends RuntimeException {

    private final List<String> violations;

    public AiWorkoutPlanValidationException(String message, List<String> violations) {
        super(message);
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }
}
