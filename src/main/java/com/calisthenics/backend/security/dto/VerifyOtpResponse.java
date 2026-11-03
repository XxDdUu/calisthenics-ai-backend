package com.calisthenics.backend.security.dto;

public record VerifyOtpResponse(String resetToken, int expiresIn) {
}
