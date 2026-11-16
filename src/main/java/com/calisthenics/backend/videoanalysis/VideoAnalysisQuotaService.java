package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.videoanalysis.dto.QuotaEntitlementResponseDto;
import com.calisthenics.backend.videoanalysis.exception.ProMembershipRequiredException;
import com.calisthenics.backend.videoanalysis.exception.QuotaExceededException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoAnalysisQuotaService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int UNLIMITED_DAILY_LIMIT_DB_VALUE = -1;

    private final UserRepository userRepository;
    private final VideoAnalysisQuotaUsageRepository quotaUsageRepository;
    private final Clock clock;

    @Transactional(readOnly = true)
    public QuotaEntitlementResponseDto getQuotaStatus(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        return getQuotaStatusForUser(user);
    }

    @Transactional(readOnly = true)
    public QuotaEntitlementResponseDto getQuotaStatusForUser(User user) {
        LocalDate quotaDate = currentQuotaDate();
        int usedToday = quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(user.getId(), quotaDate).orElse(0);
        return buildEntitlementResponse(user, quotaDate, usedToday);
    }

    @Transactional(noRollbackFor = QuotaExceededException.class)
    public QuotaEntitlementResponseDto reserveVideoAnalysisQuota(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        MembershipTier effectiveTier = resolveEffectiveTier(user);
        LocalDate quotaDate = currentQuotaDate();

        // 1. ADMIN ROLE-BASED OVERRIDE:
        // Must bypass FREE and PRO daily quota checks entirely.
        // Still records usage and analysis history!
        if (effectiveTier == MembershipTier.ADMIN) {
            quotaUsageRepository.insertIfAbsent(userId, quotaDate, UNLIMITED_DAILY_LIMIT_DB_VALUE);
            quotaUsageRepository.incrementUsedCountUnconditional(userId, quotaDate, UNLIMITED_DAILY_LIMIT_DB_VALUE);
            int currentUsed = quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(userId, quotaDate).orElse(0);

            log.debug("Recorded video analysis quota for ADMIN userId={} usedToday={} date={}",
                    userId, currentUsed, quotaDate);
            return buildEntitlementResponse(user, quotaDate, currentUsed);
        }

        // 2. STANDARD FREE / PRO QUOTA ENFORCEMENT:
        int dailyLimit = effectiveTier.getDailyLimit();
        quotaUsageRepository.insertIfAbsent(userId, quotaDate, dailyLimit);
        int updated = quotaUsageRepository.incrementUsedCountIfUnderLimit(userId, quotaDate, dailyLimit);
        int currentUsed = quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(userId, quotaDate).orElse(0);

        QuotaEntitlementResponseDto entitlement = buildEntitlementResponse(user, quotaDate, currentUsed);
        if (updated == 0) {
            log.info("Video analysis quota exceeded for userId={} tier={} used={}/{} date={}",
                    userId, effectiveTier, currentUsed, dailyLimit, quotaDate);
            throw new QuotaExceededException(
                    "Daily video analysis quota reached (" + currentUsed + "/" + dailyLimit + ").",
                    entitlement
            );
        }

        log.debug("Reserved video analysis quota for userId={} tier={} used={}/{} date={}",
                userId, effectiveTier, currentUsed, dailyLimit, quotaDate);
        return entitlement;
    }

    @Transactional
    public void releaseReservedQuota(Long userId, LocalDate quotaDate) {
        if (userId == null || quotaDate == null) {
            return;
        }
        quotaUsageRepository.decrementUsedCount(userId, quotaDate);
    }

    @Transactional(readOnly = true, noRollbackFor = ProMembershipRequiredException.class)
    public void requireProForLiveAnalysis(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        MembershipTier effectiveTier = resolveEffectiveTier(user);
        if (!effectiveTier.isLiveAnalysisUnlocked()) {
            throw new ProMembershipRequiredException(
                    "Live AI pose analysis requires a PRO membership.",
                    effectiveTier,
                    false
            );
        }
    }

    @Transactional
    public QuotaEntitlementResponseDto updateMembershipTier(Long userId, MembershipTier membershipTier) {
        if (membershipTier == null) {
            throw new IllegalArgumentException("Membership tier must not be null.");
        }
        if (membershipTier == MembershipTier.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN privileges cannot be granted via membership updates.");
        }
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        user.setMembershipTier(membershipTier);
        User saved = userRepository.save(user);

        LocalDate quotaDate = currentQuotaDate();
        if (saved.getRole() != Role.ROLE_ADMIN) {
            quotaUsageRepository.updateDailyLimit(userId, quotaDate, membershipTier.getDailyLimit());
        }
        int usedToday = quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(userId, quotaDate).orElse(0);
        return buildEntitlementResponse(saved, quotaDate, usedToday);
    }

    @Transactional
    public void syncQuotaOnRoleChange(Long userId, Role newRole, MembershipTier fallbackTier) {
        if (userId == null) {
            return;
        }
        LocalDate quotaDate = currentQuotaDate();
        if (newRole == Role.ROLE_ADMIN) {
            quotaUsageRepository.updateDailyLimit(userId, quotaDate, UNLIMITED_DAILY_LIMIT_DB_VALUE);
        } else {
            MembershipTier tier = fallbackTier != null ? fallbackTier : MembershipTier.FREE;
            quotaUsageRepository.updateDailyLimit(userId, quotaDate, tier.getDailyLimit());
        }
    }

    public LocalDate currentQuotaDate() {
        return clock.instant().atZone(VIETNAM_ZONE).toLocalDate();
    }

    public ZonedDateTime nextResetAt(LocalDate quotaDate) {
        return quotaDate.plusDays(1).atStartOfDay(VIETNAM_ZONE);
    }

    public MembershipTier resolveEffectiveTier(User user) {
        if (user != null && user.getRole() == Role.ROLE_ADMIN) {
            return MembershipTier.ADMIN;
        }
        return user != null && user.getMembershipTier() != null ? user.getMembershipTier() : MembershipTier.FREE;
    }

    private QuotaEntitlementResponseDto buildEntitlementResponse(User user, LocalDate quotaDate, int usedToday) {
        MembershipTier effectiveTier = resolveEffectiveTier(user);
        int safeUsed = Math.max(0, usedToday);

        if (effectiveTier == MembershipTier.ADMIN) {
            return QuotaEntitlementResponseDto.builder()
                    .userId(user.getId())
                    .membershipTier(MembershipTier.ADMIN)
                    .dailyLimit(null)
                    .usedToday(safeUsed)
                    .remainingToday(null)
                    .liveAnalysisUnlocked(true)
                    .unlimited(true)
                    .timezone(VIETNAM_ZONE.getId())
                    .quotaDate(quotaDate)
                    .resetsAt(nextResetAt(quotaDate))
                    .build();
        }

        int dailyLimit = effectiveTier.getDailyLimit();
        int remaining = Math.max(0, dailyLimit - safeUsed);

        return QuotaEntitlementResponseDto.builder()
                .userId(user.getId())
                .membershipTier(effectiveTier)
                .dailyLimit(dailyLimit)
                .usedToday(safeUsed)
                .remainingToday(remaining)
                .liveAnalysisUnlocked(effectiveTier.isLiveAnalysisUnlocked())
                .unlimited(false)
                .timezone(VIETNAM_ZONE.getId())
                .quotaDate(quotaDate)
                .resetsAt(nextResetAt(quotaDate))
                .build();
    }
}
