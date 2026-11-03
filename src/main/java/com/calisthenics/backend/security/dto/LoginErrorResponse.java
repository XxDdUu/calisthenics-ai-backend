package com.calisthenics.backend.security.dto;

public record LoginErrorResponse(int status, String code, String message) {
}
