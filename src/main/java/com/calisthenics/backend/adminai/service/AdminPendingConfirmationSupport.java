package com.calisthenics.backend.adminai.service;

import com.calisthenics.backend.adminai.entity.AdminAiPendingAction;
import com.calisthenics.backend.adminai.repository.AdminAiPendingActionRepository;
import com.calisthenics.backend.adminai.type.AdminPendingActionStatus;
import com.calisthenics.backend.adminai.type.AdminPendingActionType;
import com.calisthenics.backend.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Shared confirmation token lifecycle, expiry, and natural-language confirmation matching
 * for privileged Admin AI operations (Getting Started campaigns and PRO entitlements).
 */
@Service
@RequiredArgsConstructor
public class AdminPendingConfirmationSupport {

    public static final int CONFIRMATION_TTL_MINUTES = 30;
    private static final Set<String> NEGATION_TOKENS = Set.of(
            "no", "not", "don't", "dont", "do not", "cancel", "abort", "stop", "deny", "reject", "never", "wait", "hold"
    );

    private final AdminAiPendingActionRepository pendingActionRepository;

    @Transactional(readOnly = true)
    public Optional<AdminAiPendingAction> findActivePendingConfirmation(Long adminId, Long conversationId) {
        if (adminId == null || conversationId == null) {
            return Optional.empty();
        }
        return pendingActionRepository
                .findFirstByAdminUserIdAndConversationIdAndStatusOrderByCreatedAtDesc(
                        adminId, conversationId, AdminPendingActionStatus.PENDING)
                .filter(a -> a.getExpiresAt().isAfter(ZonedDateTime.now()));
    }

    @Transactional
    public void expirePendingActionsForConversation(Long adminId, Long conversationId) {
        if (adminId == null || conversationId == null) {
            return;
        }
        List<AdminAiPendingAction> stale = pendingActionRepository
                .findAllByAdminUserIdAndConversationIdAndStatus(adminId, conversationId, AdminPendingActionStatus.PENDING);
        if (stale.isEmpty()) {
            return;
        }
        ZonedDateTime now = ZonedDateTime.now();
        for (AdminAiPendingAction old : stale) {
            old.setStatus(AdminPendingActionStatus.EXPIRED);
            old.setResolvedAt(now);
        }
        pendingActionRepository.saveAll(stale);
    }

    @Transactional
    public AdminAiPendingAction validatePendingActionForResolution(
            User admin,
            String token,
            AdminPendingActionType expectedTypeOrNull
    ) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("A valid confirmationToken is required.");
        }
        AdminAiPendingAction action = pendingActionRepository
                .findByConfirmationTokenAndAdminUserId(token, admin.getId())
                .orElseThrow(() -> new IllegalArgumentException("Pending confirmation request not found."));

        if (expectedTypeOrNull != null && action.getActionType() != expectedTypeOrNull) {
            throw new IllegalArgumentException("Confirmation token does not match requested action " + expectedTypeOrNull);
        }
        if (action.getStatus() != AdminPendingActionStatus.PENDING) {
            throw new IllegalStateException(
                    "Confirmation request was already " + action.getStatus().name().toLowerCase(Locale.US) + ".");
        }
        if (action.getExpiresAt().isBefore(ZonedDateTime.now())) {
            action.setStatus(AdminPendingActionStatus.EXPIRED);
            action.setResolvedAt(ZonedDateTime.now());
            pendingActionRepository.save(action);
            throw new IllegalStateException("Confirmation request has expired. Please request approval again.");
        }
        return action;
    }

    public boolean isExplicitConfirmationMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.trim().toLowerCase(Locale.US);
        if (containsAnyToken(lower, NEGATION_TOKENS)) {
            return false;
        }
        String normalized = lower.replaceAll("[,]+", " ")
                .replaceAll("[.!]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.matches(
                "^(?:yes|yeah|yep|confirm|confirmed|approve|approved|proceed|ok|okay|sure|go ahead)"
                        + "(?:\\s+(?:and\\s+)?(?:please\\s+)?(?:confirm|approve|publish|unpublish|grant|revoke|remove|pro|access|membership|proceed|it|do it|now|please|campaign|program|action))*$");
    }

    public boolean isExplicitCancellationMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.trim()
                .toLowerCase(Locale.US)
                .replaceAll("[.!]+$", "")
                .replaceAll("\\s+", " ")
                .trim();
        return normalized.equals("no") || normalized.equals("cancel") || normalized.equals("abort")
                || normalized.equals("stop") || normalized.equals("deny") || normalized.equals("reject")
                || normalized.startsWith("cancel ") || normalized.startsWith("no,") || normalized.startsWith("no ")
                || normalized.startsWith("don't ") || normalized.startsWith("do not ");
    }

    private boolean containsAnyToken(String lowerText, Set<String> tokens) {
        for (String token : tokens) {
            if (lowerText.matches(".*\\b" + token.replace("'", "'?") + "\\b.*")) {
                return true;
            }
        }
        return false;
    }
}
