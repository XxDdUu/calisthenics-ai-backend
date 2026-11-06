package com.calisthenics.backend.security.dto;

public record RegisterInitiationResponse(
        int status,
        String message,
        String email,
        int expiresIn,
        int resendCooldown
) {
}

