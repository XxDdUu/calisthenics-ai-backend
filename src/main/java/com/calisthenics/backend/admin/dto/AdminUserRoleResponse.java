package com.calisthenics.backend.admin.dto;

import com.calisthenics.backend.user.Role;

public record AdminUserRoleResponse(
        Long userId,
        Role role
) {
}
