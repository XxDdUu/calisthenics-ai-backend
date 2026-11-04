package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.user.Role;

public record AdminBootstrapResponse(
        Long userId,
        String email,
        Role role,
        String message
) {
}
