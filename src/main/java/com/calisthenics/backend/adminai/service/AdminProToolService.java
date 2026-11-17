package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.admin.AdminService;
import com.calisthenics.backend.admin.dto.AdminUserResponse;
import com.calisthenics.backend.adminai.dto.data.AdminConfirmationDecisionResponse;
import com.calisthenics.backend.adminai.dto.data.AdminPendingConfirmationResponse;
import com.calisthenics.backend.adminai.dto.data.AdminProToolResultResponse;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecuteRequest;
import com.calisthenics.backend.adminai.dto.data.AdminToolExecutionRecord;
import com.calisthenics.backend.adminai.entity.AdminAiConversation;
import com.calisthenics.backend.adminai.entity.AdminAiPendingAction;
import com.calisthenics.backend.adminai.repository.AdminAiConversationRepository;
import com.calisthenics.backend.adminai.repository.AdminAiPendingActionRepository;
import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import com.calisthenics.backend.adminai.type.AdminPendingActionType;
import com.calisthenics.backend.adminai.type.AdminToolName;
import com.calisthenics.backend.profile.entity.ClientProfile;
import com.calisthenics.backend.profile.entity.CoachProfile;
import com.calisthenics.backend.profile.repository.ClientProfileRepository;
import com.calisthenics.backend.profile.repository.CoachProfileRepository;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Authorized Admin AI backend tool coordinator for user PRO entitlement management
 * (grant, revoke, and status check) with strict ROLE_ADMIN enforcement, unambiguous
 * target user resolution, mandatory two-step confirmation, and full audit logging.
 */
@Service
@RequiredArgsConstructor
public class AdminProToolService {

    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile(
            "^(?:user\\s+(?:id\\s*)?[:#]?|user#|id\\s*[:#]?|#)?(\\d+)$",
            Pattern.CASE_INSENSITIVE);
    private static final int MAX_CLARIFICATION_CANDIDATES = 8;

    private final AdminAuthorizationService authorizationService;
    private final AdminService adminService;
    private final UserRepository userRepository;
    private final ClientProfileRepository clientProfileRepository;
    private final CoachProfileRepository coachProfileRepository;
    private final AdminAiPendingActionRepository pendingActionRepository;
    private final AdminAiConversationRepository conversationRepository;
    private final AdminPendingConfirmationSupport confirmationSupport;

    public boolean isProTool(AdminToolName toolName) {
        return toolName == AdminToolName.GRANT_PRO
                || toolName == AdminToolName.REVOKE_PRO
                || toolName == AdminToolName.CHECK_PRO_STATUS;
    }

    @Transactional
    public AdminProToolResultResponse executeTool(
            User admin,
            AdminToolName toolName,
            AdminToolExecuteRequest request
    ) {
        User verifiedAdmin = authorizationService.requireAdminToolAccess(toolName);
        if (admin != null && !Objects.equals(admin.getId(), verifiedAdmin.getId())) {
            throw new AccessDeniedException("Authenticated admin principal mismatch.");
        }
        if (!isProTool(toolName)) {
            throw new IllegalArgumentException("Unsupported PRO management tool: " + toolName);
        }

        AdminToolExecuteRequest safeRequest = request != null ? request : new AdminToolExecuteRequest();
        ResolutionOutcome resolution = resolveTargetUsers(safeRequest.getUserId(), safeRequest.getUserIdentifier());

        if (resolution.outcomeStatus() == ResolutionStatus.MISSING_TARGET) {
            return AdminProToolResultResponse.builder()
                    .action(toolName.name())
                    .outcomeStatus("MISSING_TARGET")
                    .confirmationRequired(false)
                    .executed(false)
                    .message("Please specify the target user's **email**, **username/display name**, or **numeric user ID** "
                            + "(for example: `Grant PRO to user@example.com` or `Revoke PRO from user123`).")
                    .build();
        }

        if (resolution.outcomeStatus() == ResolutionStatus.NOT_FOUND) {
            return AdminProToolResultResponse.builder()
                    .action(toolName.name())
                    .outcomeStatus("USER_NOT_FOUND")
                    .confirmationRequired(false)
                    .executed(false)
                    .message("No user account matched `" + safeLabel(safeRequest.getUserIdentifier(), safeRequest.getUserId())
                            + "`. No account was modified. Please verify the exact email, username, or user ID and try again.")
                    .build();
        }

        if (resolution.outcomeStatus() == ResolutionStatus.AMBIGUOUS) {
            List<AdminUserResponse> candidateDtos = resolution.candidates().stream()
                    .limit(MAX_CLARIFICATION_CANDIDATES)
                    .map(u -> adminService.getUser(u.getId()))
                    .toList();
            authorizationService.logToolExecution(
                    toolName,
                    verifiedAdmin.getId(),
                    null,
                    "AMBIGUOUS_TARGET identifier=" + safeLabel(safeRequest.getUserIdentifier(), safeRequest.getUserId())
                            + " matches=" + candidateDtos.size()
            );
            return AdminProToolResultResponse.builder()
                    .action(toolName.name())
                    .outcomeStatus("AMBIGUOUS_TARGET")
                    .confirmationRequired(false)
                    .executed(false)
                    .candidates(candidateDtos)
                    .message(buildClarificationMessage(
                            safeLabel(safeRequest.getUserIdentifier(), safeRequest.getUserId()),
                            candidateDtos,
                            toolName))
                    .build();
        }

        User targetUser = resolution.candidates().get(0);
        AdminUserResponse current = adminService.getUser(targetUser.getId());

        if (toolName == AdminToolName.CHECK_PRO_STATUS) {
            authorizationService.logToolExecution(
                    AdminToolName.CHECK_PRO_STATUS,
                    verifiedAdmin.getId(),
                    current.id(),
                    "CHECK_PRO_STATUS effectiveTier=" + current.membershipTier() + ",source=" + current.membershipSource()
            );
            return AdminProToolResultResponse.builder()
                    .action(AdminToolName.CHECK_PRO_STATUS.name())
                    .outcomeStatus("STATUS_INFO")
                    .confirmationRequired(false)
                    .executed(true)
                    .targetUserId(current.id())
                    .targetUser(current)
                    .message(buildStatusMessage(current))
                    .build();
        }

        boolean isGrant = toolName == AdminToolName.GRANT_PRO;
        if (isGrant && current.adminGrantedPro()) {
            return AdminProToolResultResponse.builder()
                    .action(toolName.name())
                    .outcomeStatus("NO_CHANGE_NEEDED")
                    .confirmationRequired(false)
                    .executed(false)
                    .targetUserId(current.id())
                    .targetUser(current)
                    .message(formatAccountLabel(current) + " already has an active **admin-granted unlimited PRO** entitlement "
                            + "(source: `" + current.membershipSource() + "`, unlimited quota enabled). No changes were made.")
                    .build();
        }

        if (!isGrant && !current.adminGrantedPro()) {
            if (current.paidPro()) {
                return AdminProToolResultResponse.builder()
                        .action(toolName.name())
                        .outcomeStatus("PAID_SUBSCRIPTION_PROTECTED")
                        .confirmationRequired(false)
                        .executed(false)
                        .targetUserId(current.id())
                        .targetUser(current)
                        .message(formatAccountLabel(current) + " currently holds a **paid PRO subscription** (`"
                                + current.membershipSource() + "`) and does not have an active admin-granted PRO entitlement. "
                                + "Paid subscriptions are preserved independently and cannot be revoked via admin PRO revocation.")
                        .build();
            }
            return AdminProToolResultResponse.builder()
                    .action(toolName.name())
                    .outcomeStatus("NO_CHANGE_NEEDED")
                    .confirmationRequired(false)
                    .executed(false)
                    .targetUserId(current.id())
                    .targetUser(current)
                    .message(formatAccountLabel(current) + " currently has `" + current.membershipTier()
                            + "` access and does not have an active admin-granted PRO entitlement to revoke.")
                    .build();
        }

        AdminPendingActionType actionType = isGrant
                ? AdminPendingActionType.GRANT_PRO
                : AdminPendingActionType.REVOKE_PRO;
        String cleanReason = sanitizeReason(safeRequest.getReason());

        if (!Boolean.TRUE.equals(safeRequest.getConfirmed())) {
            AdminPendingConfirmationResponse pending = createPendingAction(
                    verifiedAdmin,
                    safeRequest.getConversationId(),
                    actionType,
                    current,
                    cleanReason
            );
            authorizationService.logToolExecution(
                    toolName,
                    verifiedAdmin.getId(),
                    current.id(),
                    "PENDING_CONFIRMATION token=" + pending.getConfirmationToken()
                            + ",targetUserId=" + current.id()
                            + ",action=" + actionType.name()
            );
            return AdminProToolResultResponse.builder()
                    .action(actionType.name())
                    .outcomeStatus("CONFIRMATION_REQUIRED")
                    .confirmationRequired(true)
                    .executed(false)
                    .targetUserId(current.id())
                    .targetUser(current)
                    .pendingConfirmation(pending)
                    .message(buildPendingConfirmationMarkdown(actionType, current, cleanReason, pending.getConfirmationToken()))
                    .build();
        }

        if (safeRequest.getConfirmationToken() == null || safeRequest.getConfirmationToken().isBlank()) {
            throw new IllegalArgumentException("Explicit admin confirmation token is required before modifying PRO access.");
        }

        AdminAiPendingAction action = confirmationSupport.validatePendingActionForResolution(
                verifiedAdmin,
                safeRequest.getConfirmationToken(),
                actionType
        );
        if (!Objects.equals(action.getTargetUserId(), current.id())) {
            throw new IllegalArgumentException("Confirmation token does not match target user ID " + current.id() + ".");
        }

        String effectiveReason = cleanReason != null ? cleanReason : action.getActionReason();
        action.setStatus(AdminPendingActionStatus.CONFIRMED);
        action.setResolvedAt(ZonedDateTime.now());
        pendingActionRepository.save(action);

        AdminUserResponse updated = executeProChange(verifiedAdmin, actionType, current, effectiveReason);
        return AdminProToolResultResponse.builder()
                .action(actionType.name())
                .outcomeStatus("EXECUTED")
                .confirmationRequired(false)
                .executed(true)
                .targetUserId(updated.id())
                .targetUser(updated)
                .message(buildExecutionResultMarkdown(actionType, current, updated))
                .build();
    }

    @Transactional
    public AdminConfirmationDecisionResponse resolveConfirmationDecision(
            User admin,
            String confirmationToken,
            boolean approved,
            Long conversationIdHint
    ) {
        User verifiedAdmin = authorizationService.requireAuthenticatedAdmin();
        if (admin != null && !Objects.equals(admin.getId(), verifiedAdmin.getId())) {
            throw new AccessDeniedException("Authenticated admin principal mismatch.");
        }

        AdminAiPendingAction action = confirmationSupport.validatePendingActionForResolution(
                verifiedAdmin, confirmationToken, null);
        if (action.getActionType() != AdminPendingActionType.GRANT_PRO
                && action.getActionType() != AdminPendingActionType.REVOKE_PRO) {
            throw new IllegalArgumentException("Confirmation token is not a PRO management action.");
        }
        if (action.getTargetUserId() == null) {
            throw new IllegalStateException("Pending PRO confirmation is missing targetUserId.");
        }

        Long convId = action.getConversation() != null ? action.getConversation().getId() : conversationIdHint;
        AdminToolName toolName = action.getActionType() == AdminPendingActionType.GRANT_PRO
                ? AdminToolName.GRANT_PRO
                : AdminToolName.REVOKE_PRO;
        AdminUserResponse current = adminService.getUser(action.getTargetUserId());

        if (!approved) {
            action.setStatus(AdminPendingActionStatus.CANCELLED);
            action.setResolvedAt(ZonedDateTime.now());
            pendingActionRepository.save(action);

            authorizationService.logToolExecution(
                    toolName,
                    verifiedAdmin.getId(),
                    current.id(),
                    "CANCELLED action=" + action.getActionType() + ",targetUserId=" + current.id()
            );
            return AdminConfirmationDecisionResponse.builder()
                    .confirmationToken(confirmationToken)
                    .actionType(action.getActionType())
                    .status(AdminPendingActionStatus.CANCELLED)
                    .conversationId(convId)
                    .updatedUser(current)
                    .reply("Cancelled "
                            + (action.getActionType() == AdminPendingActionType.GRANT_PRO ? "granting PRO to " : "revoking PRO from ")
                            + formatAccountLabel(current)
                            + ". No entitlement or quota changes were made.")
                    .build();
        }

        action.setStatus(AdminPendingActionStatus.CONFIRMED);
        action.setResolvedAt(ZonedDateTime.now());
        pendingActionRepository.save(action);

        AdminUserResponse updated = executeProChange(verifiedAdmin, action.getActionType(), current, action.getActionReason());
        String reply = buildExecutionResultMarkdown(action.getActionType(), current, updated);

        return AdminConfirmationDecisionResponse.builder()
                .confirmationToken(confirmationToken)
                .actionType(action.getActionType())
                .status(AdminPendingActionStatus.CONFIRMED)
                .conversationId(convId)
                .reply(reply)
                .updatedUser(updated)
                .execution(AdminToolExecutionRecord.builder()
                        .toolName(toolName.getToolName())
                        .status("EXECUTED")
                        .parametersSummary("targetUserId=" + updated.id() + ",action=" + action.getActionType())
                        .summary(reply)
                        .recordCount(1L)
                        .executedAt(ZonedDateTime.now())
                        .build())
                .build();
    }

    private AdminUserResponse executeProChange(
            User verifiedAdmin,
            AdminPendingActionType actionType,
            AdminUserResponse previous,
            String reason
    ) {
        AdminToolName toolName = actionType == AdminPendingActionType.GRANT_PRO
                ? AdminToolName.GRANT_PRO
                : AdminToolName.REVOKE_PRO;

        AdminUserResponse updated = actionType == AdminPendingActionType.GRANT_PRO
                ? adminService.grantAdminPro(previous.id(), verifiedAdmin.getId(), reason)
                : adminService.revokeAdminPro(previous.id(), verifiedAdmin.getId(), reason);

        authorizationService.logToolExecution(
                toolName,
                verifiedAdmin.getId(),
                updated.id(),
                "EXECUTED action=" + actionType.name()
                        + ",adminUserId=" + verifiedAdmin.getId()
                        + ",targetUserId=" + updated.id()
                        + ",previousTier=" + previous.membershipTier()
                        + ",newTier=" + updated.membershipTier()
                        + ",paidPro=" + updated.paidPro()
                        + ",unlimited=" + updated.unlimited()
                        + ",result=SUCCESS"
        );
        return updated;
    }

    private AdminPendingConfirmationResponse createPendingAction(
            User admin,
            Long conversationId,
            AdminPendingActionType actionType,
            AdminUserResponse target,
            String reason
    ) {
        confirmationSupport.expirePendingActionsForConversation(admin.getId(), conversationId);

        AdminAiConversation convRef = conversationId != null
                ? conversationRepository.findByIdAndAdminUserId(conversationId, admin.getId()).orElse(null)
                : null;

        String actionPhrase = actionType == AdminPendingActionType.GRANT_PRO
                ? "granting unlimited admin PRO access to"
                : "revoking admin-granted PRO access from";
        String summary = "Explicit Admin confirmation required before " + actionPhrase + " "
                + target.fullName() + " (User #" + target.id()
                + ", current tier: " + target.membershipTier() + " / " + target.membershipSource() + "). "
                + (target.paidPro() && actionType == AdminPendingActionType.REVOKE_PRO
                ? "Paid subscription will remain active. "
                : "")
                + "Reply with 'Confirm' or click Confirm to proceed.";

        AdminAiPendingAction saved = pendingActionRepository.save(AdminAiPendingAction.builder()
                .confirmationToken(UUID.randomUUID().toString())
                .adminUser(admin)
                .conversation(convRef)
                .actionType(actionType)
                .targetUserId(target.id())
                .actionReason(reason)
                .status(AdminPendingActionStatus.PENDING)
                .summary(summary)
                .expiresAt(ZonedDateTime.now().plusMinutes(AdminPendingConfirmationSupport.CONFIRMATION_TTL_MINUTES))
                .build());

        return AdminPendingConfirmationResponse.builder()
                .confirmationToken(saved.getConfirmationToken())
                .actionType(saved.getActionType())
                .status(saved.getStatus())
                .targetUserId(target.id())
                .targetUserName(target.fullName())
                .targetUserEmail(target.email())
                .targetUserCurrentTier(target.membershipTier())
                .targetUserMembershipSource(target.membershipSource())
                .targetUserPaidPro(target.paidPro())
                .targetUserAdminGrantedPro(target.adminGrantedPro())
                .reason(reason)
                .summary(summary)
                .createdAt(saved.getCreatedAt())
                .expiresAt(saved.getExpiresAt())
                .build();
    }

    private enum ResolutionStatus {
        RESOLVED_SINGLE,
        AMBIGUOUS,
        NOT_FOUND,
        MISSING_TARGET
    }

    private record ResolutionOutcome(ResolutionStatus outcomeStatus, List<User> candidates) {}

    private ResolutionOutcome resolveTargetUsers(Long explicitUserId, String rawIdentifier) {
        if (explicitUserId != null) {
            return userRepository.findById(explicitUserId)
                    .map(u -> new ResolutionOutcome(ResolutionStatus.RESOLVED_SINGLE, List.of(u)))
                    .orElseGet(() -> new ResolutionOutcome(ResolutionStatus.NOT_FOUND, List.of()));
        }

        String cleaned = cleanIdentifier(rawIdentifier);
        if (cleaned == null) {
            return new ResolutionOutcome(ResolutionStatus.MISSING_TARGET, List.of());
        }

        Matcher idMatcher = NUMERIC_ID_PATTERN.matcher(cleaned);
        if (idMatcher.matches()) {
            try {
                long parsedId = Long.parseLong(idMatcher.group(1));
                return userRepository.findById(parsedId)
                        .map(u -> new ResolutionOutcome(ResolutionStatus.RESOLVED_SINGLE, List.of(u)))
                        .orElseGet(() -> new ResolutionOutcome(ResolutionStatus.NOT_FOUND, List.of()));
            } catch (NumberFormatException ignored) {
                return new ResolutionOutcome(ResolutionStatus.NOT_FOUND, List.of());
            }
        }

        if (cleaned.contains("@")) {
            Optional<User> exactEmail = userRepository.findByEmailIgnoreCase(cleaned);
            if (exactEmail.isPresent()) {
                return new ResolutionOutcome(ResolutionStatus.RESOLVED_SINGLE, List.of(exactEmail.get()));
            }
            String lowerEmail = cleaned.toLowerCase(Locale.US);
            List<User> partialEmailMatches = userRepository.findAll().stream()
                    .filter(u -> u.getEmail() != null && u.getEmail().toLowerCase(Locale.US).contains(lowerEmail))
                    .toList();
            if (partialEmailMatches.isEmpty()) {
                return new ResolutionOutcome(ResolutionStatus.NOT_FOUND, List.of());
            }
            return new ResolutionOutcome(ResolutionStatus.AMBIGUOUS, partialEmailMatches);
        }

        List<User> allUsers = userRepository.findAll();
        Map<Long, String> clientDisplayNames = new HashMap<>();
        for (ClientProfile cp : clientProfileRepository.findAll()) {
            if (cp.getUser() != null && cp.getUser().getId() != null && cp.getDisplayName() != null) {
                clientDisplayNames.put(cp.getUser().getId(), cp.getDisplayName().trim());
            }
        }
        Map<Long, String> coachDisplayNames = new HashMap<>();
        for (CoachProfile cp : coachProfileRepository.findAll()) {
            if (cp.getUser() != null && cp.getUser().getId() != null && cp.getDisplayName() != null) {
                coachDisplayNames.put(cp.getUser().getId(), cp.getDisplayName().trim());
            }
        }

        String lowerQuery = cleaned.toLowerCase(Locale.US);
        Map<Long, User> exactMatches = new LinkedHashMap<>();
        for (User u : allUsers) {
            if (equalsIgnoreCaseTrim(u.getFullName(), cleaned)
                    || equalsIgnoreCaseTrim(emailLocalPart(u.getEmail()), cleaned)
                    || equalsIgnoreCaseTrim(clientDisplayNames.get(u.getId()), cleaned)
                    || equalsIgnoreCaseTrim(coachDisplayNames.get(u.getId()), cleaned)) {
                exactMatches.put(u.getId(), u);
            }
        }
        if (exactMatches.size() == 1) {
            return new ResolutionOutcome(ResolutionStatus.RESOLVED_SINGLE, List.copyOf(exactMatches.values()));
        }
        if (exactMatches.size() > 1) {
            return new ResolutionOutcome(ResolutionStatus.AMBIGUOUS, List.copyOf(exactMatches.values()));
        }

        Map<Long, User> partialMatches = new LinkedHashMap<>();
        for (User u : allUsers) {
            if (containsIgnoreCase(u.getFullName(), lowerQuery)
                    || containsIgnoreCase(emailLocalPart(u.getEmail()), lowerQuery)
                    || containsIgnoreCase(clientDisplayNames.get(u.getId()), lowerQuery)
                    || containsIgnoreCase(coachDisplayNames.get(u.getId()), lowerQuery)) {
                partialMatches.put(u.getId(), u);
            }
        }
        if (partialMatches.isEmpty()) {
            return new ResolutionOutcome(ResolutionStatus.NOT_FOUND, List.of());
        }
        if (partialMatches.size() == 1) {
            return new ResolutionOutcome(ResolutionStatus.RESOLVED_SINGLE, List.copyOf(partialMatches.values()));
        }
        return new ResolutionOutcome(ResolutionStatus.AMBIGUOUS, List.copyOf(partialMatches.values()));
    }

    private String buildPendingConfirmationMarkdown(
            AdminPendingActionType actionType,
            AdminUserResponse target,
            String reason,
            String confirmationToken
    ) {
        boolean isGrant = actionType == AdminPendingActionType.GRANT_PRO;
        StringBuilder sb = new StringBuilder();
        sb.append("### Confirmation Required: ")
                .append(isGrant ? "Grant Unlimited PRO Access" : "Revoke Admin-Granted PRO Access")
                .append("\n\n");
        sb.append("No account changes have been made yet. Review the target account and confirm to proceed:\n\n");
        sb.append("- **Target Account**: **").append(target.fullName()).append("** (User #").append(target.id()).append(")\n");
        sb.append("- **Email**: `").append(target.email()).append("`\n");
        sb.append("- **Current Tier / Source**: ").append(target.membershipTier())
                .append(" (").append(target.membershipSource()).append(")\n");
        sb.append("- **Paid Subscription Active**: ").append(target.paidPro() ? "Yes (preserved independently)" : "No").append("\n");
        sb.append("- **Requested Action**: `").append(actionType.name()).append("`\n");
        if (isGrant) {
            sb.append("- **Result if Confirmed**: Grants **unlimited PRO** access across all PRO features and AI/video quotas.\n");
        } else {
            sb.append("- **Result if Confirmed**: Removes admin-granted PRO access. ")
                    .append(target.paidPro()
                            ? "Because this user has an active paid subscription, their **paid PRO tier remains active**.\n"
                            : "Effective tier will return to **FREE**.\n");
        }
        if (reason != null) {
            sb.append("- **Reason**: ").append(reason).append("\n");
        }
        sb.append("- **Confirmation Token**: `").append(confirmationToken).append("`\n\n");
        sb.append("Click **Confirm** below or reply `Confirm` to execute, or `Cancel` to abort.");
        return sb.toString();
    }

    private String buildExecutionResultMarkdown(
            AdminPendingActionType actionType,
            AdminUserResponse before,
            AdminUserResponse after
    ) {
        boolean isGrant = actionType == AdminPendingActionType.GRANT_PRO;
        StringBuilder sb = new StringBuilder();
        sb.append("### ")
                .append(isGrant ? "PRO Access Granted" : "Admin-Granted PRO Revoked")
                .append("\n\n");
        sb.append("- **Target Account**: **").append(after.fullName()).append("** (User #").append(after.id()).append(")\n");
        sb.append("- **Email**: `").append(after.email()).append("`\n");
        sb.append("- **Previous Tier**: ").append(before.membershipTier()).append(" (").append(before.membershipSource()).append(")\n");
        sb.append("- **Updated Tier**: **").append(after.membershipTier()).append("** (").append(after.membershipSource()).append(")\n");
        sb.append("- **Unlimited Quota Access**: ").append(after.unlimited() ? "Enabled" : "Standard limits").append("\n");
        if (!isGrant && after.paidPro()) {
            sb.append("- **Paid Subscription Status**: Preserved (**PRO** subscription remains active)\n");
        }
        sb.append("- **Audit Log**: Recorded acting admin, target user `#").append(after.id()).append("`, action `")
                .append(actionType.name()).append("`, and timestamp.");
        return sb.toString();
    }

    private String buildClarificationMessage(
            String query,
            List<AdminUserResponse> candidates,
            AdminToolName toolName
    ) {
        String verb = toolName == AdminToolName.REVOKE_PRO
                ? "revoke PRO from"
                : (toolName == AdminToolName.CHECK_PRO_STATUS ? "check PRO status for" : "grant PRO to");
        StringBuilder sb = new StringBuilder();
        sb.append("### Clarification Needed — Multiple Users Matched `").append(query).append("`\n\n");
        sb.append("No accounts were modified because **").append(candidates.size())
                .append("** accounts matched your request. Please specify the exact **email** or **user ID** you want to ")
                .append(verb).append(":\n\n");
        for (AdminUserResponse c : candidates) {
            sb.append("- **User #").append(c.id()).append("** — ").append(c.fullName())
                    .append(" (`").append(c.email()).append("`) · ")
                    .append(c.membershipTier()).append(" [").append(c.membershipSource()).append("]\n");
        }
        return sb.toString();
    }

    private String buildStatusMessage(AdminUserResponse u) {
        return "### Membership & PRO Entitlement Status\n\n"
                + "- **Account**: **" + u.fullName() + "** (User #" + u.id() + ")\n"
                + "- **Email**: `" + u.email() + "`\n"
                + "- **Effective Tier**: **" + u.membershipTier() + "** (" + u.membershipSource() + ")\n"
                + "- **Unlimited Quota**: " + (u.unlimited() ? "Yes" : "No") + "\n"
                + "- **Paid Subscription**: " + (u.paidPro() ? "Active" : "None") + "\n"
                + "- **Admin-Granted PRO**: " + (u.adminGrantedPro() ? "Active" : u.adminProStatus());
    }

    private String formatAccountLabel(AdminUserResponse u) {
        return "**" + u.fullName() + "** (User #" + u.id() + ", `" + u.email() + "`)";
    }

    private String cleanIdentifier(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim()
                .replaceAll("^[\"'`]+", "")
                .replaceAll("[\"'`.,!?;:]+$", "")
                .trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safeLabel(String identifier, Long userId) {
        if (userId != null) {
            return "#" + userId;
        }
        return identifier != null ? identifier.trim() : "";
    }

    private String sanitizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        String cleaned = reason.trim();
        if (cleaned.isEmpty()) {
            return null;
        }
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    private String emailLocalPart(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        return at > 0 ? email.substring(0, at) : email;
    }

    private boolean equalsIgnoreCaseTrim(String value, String query) {
        return value != null && value.trim().equalsIgnoreCase(query);
    }

    private boolean containsIgnoreCase(String value, String lowerQuery) {
        return value != null && value.toLowerCase(Locale.US).contains(lowerQuery);
    }
}
