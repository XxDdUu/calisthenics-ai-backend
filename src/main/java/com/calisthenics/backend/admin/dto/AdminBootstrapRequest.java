package com.calisthenics.backend.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AdminBootstrapRequest(
        @NotBlank @Email String email
) {
}
