package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.entity.AdminAiAuditLog;
import com.calisthenics.backend.adminai.repository.AdminAiAuditLogRepository;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.security.UserPrincipal;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAuthorizationService {

    private final UserRepository userRepository;
    private final AdminAiAuditLogRepository adminAiAuditLogRepository;

    /**
     * Independently enforces ROLE_ADMIN authorization regardless of caller layer
     * (HTTP controller, tool router, or direct service call).
     */
    @Transactional(readOnly = true)
    public User requireAuthenticatedAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AccessDeniedException("Admin authentication is required");
        }

        boolean hasAdminAuthority = authentication.getAuthorities() != null
                && authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(Role.ROLE_ADMIN.name()::equals);
        if (!hasAdminAuthority) {
            throw new AccessDeniedException("Admin privileges (ROLE_ADMIN) are required");
        }

        Object principal = authentication.getPrincipal();
        User adminUser = null;
        if (principal instanceof UserPrincipal userPrincipal && userPrincipal.getId() != null) {
            adminUser = userRepository.findById(userPrincipal.getId()).orElse(null);
        } else if (authentication.getName() != null && !authentication.getName().isBlank()) {
            adminUser = userRepository.findByEmail(authentication.getName()).orElse(null);
        }

        if (adminUser == null || adminUser.getRole() != Role.ROLE_ADMIN) {
            throw new AccessDeniedException("Verified ROLE_ADMIN database account is required");
        }
        return adminUser;
    }

    /**
     * All global data access tools are strictly read-only.
     */
    public User requireReadOnlyToolAccess(AdminToolName toolName) {
        User admin = requireAuthenticatedAdmin();
        if (toolName == null) {
            throw new AccessDeniedException("Valid admin tool name is required");
        }
        return admin;
    }

    @Transactional
    public void logDataAccess(Long adminUserId, String action, String targetType, Long targetId, String details) {
        if (adminUserId == null) {
            return;
        }
        User adminRef = userRepository.getReferenceById(adminUserId);
        String safeAction = truncate(action, 64);
        String safeTargetType = truncate(targetType, 64);
        adminAiAuditLogRepository.save(AdminAiAuditLog.builder()
                .adminUser(adminRef)
                .action(safeAction)
                .targetType(safeTargetType)
                .targetId(targetId)
                .details(details)
                .build());
    }

    @Transactional
    public void logToolExecution(AdminToolName toolName, Long adminUserId, Long targetId, String details) {
        logDataAccess(
                adminUserId,
                "TOOL_" + toolName.name(),
                toolName.getToolName(),
                targetId,
                details
        );
    }

    private String truncate(String value, int maxLen) {
        if (value == null) {
            return "UNKNOWN";
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLen ? trimmed : trimmed.substring(0, maxLen);
    }
}
