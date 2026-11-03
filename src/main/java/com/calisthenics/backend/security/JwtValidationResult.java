package com.calisthenics.backend.security;

public enum JwtValidationResult {
    VALID,
    EXPIRED,
    INVALID_SIGNATURE,
    MALFORMED,
    UNSUPPORTED,
    EMPTY,
    INVALID_TYPE
}

