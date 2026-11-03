package com.calisthenics.backend.security.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "Reset token is required")
    private String resetToken;

    @NotBlank(message = "New password is required")
    @Size(max = 256, message = "Password is too long")
    private String newPassword;

    @NotBlank(message = "Password confirmation is required")
    @Size(max = 256, message = "Password is too long")
    private String confirmPassword;
}
