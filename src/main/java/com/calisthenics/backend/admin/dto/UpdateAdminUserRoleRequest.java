package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.user.Role;
import jakarta.validation.constraints.NotNull;

public record UpdateAdminUserRoleRequest(
        @NotNull Role role
) {
}
