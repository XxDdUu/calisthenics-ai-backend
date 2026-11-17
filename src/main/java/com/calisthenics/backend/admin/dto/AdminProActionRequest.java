package com.calisthenics.backend.admin.dto;

import jakarta.validation.constraints.Size;

public record AdminProActionRequest(
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason
) {
}
