package com.calisthenics.backend.security.passwordreset;

import org.springframework.http.HttpStatus;

public class PasswordResetException extends RuntimeException {

    private final String code;
    private final HttpStatus status;

    public PasswordResetException(String code, String message, HttpStatus status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() {
        return code;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
