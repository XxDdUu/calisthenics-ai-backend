package com.calisthenics.backend.videoanalysis;

import com.calisthenics.backend.user.EntitlementGrantSource;
import com.calisthenics.backend.user.EntitlementStatus;
import com.calisthenics.backend.user.MembershipEntitlementRepository;
import com.calisthenics.backend.user.MembershipTier;
import com.calisthenics.backend.user.Role;
import com.calisthenics.backend.user.User;
import com.calisthenics.backend.user.UserRepository;
import com.calisthenics.backend.user.dto.LiveAnalysisUsageDto;
import com.calisthenics.backend.user.dto.MembershipUsageDto;
import com.calisthenics.backend.user.dto.UserUsageResponseDto;
import com.calisthenics.backend.user.dto.VideoAnalysisUsageDto;
import com.calisthenics.backend.videoanalysis.dto.QuotaEntitlementResponseDto;
import com.calisthenics.backend.videoanalysis.exception.ProMembershipRequiredException;
import com.calisthenics.backend.videoanalysis.exception.QuotaExceededException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
public class VideoAnalysisQuotaService {

    public static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final int UNLIMITED_DAILY_LIMIT_DB_VALUE = -1;

    private final UserRepository userRepository;
    private final VideoAnalysisQuotaUsageRepository quotaUsageRepository;
    private final Clock clock;
    private final MembershipEntitlementRepository membershipEntitlementRepository;

    public VideoAnalysisQuotaService(
            UserRepository userRepository,
            VideoAnalysisQuotaUsageRepository quotaUsageRepository,
            Clock clock
    ) {
        this(userRepository, quotaUsageRepository, clock, null);
    }

    @Autowired
    public VideoAnalysisQuotaService(
            UserRepository userRepository,
            VideoAnalysisQuotaUsageRepository quotaUsageRepository,
            Clock clock,
            @Autowired(required = false) MembershipEntitlementRepository membershipEntitlementRepository
    ) {
        this.userRepository = userRepository;
        this.quotaUsageRepository = quotaUsageRepository;
        this.clock = clock;
        this.membershipEntitlementRepository = membershipEntitlementRepository;
    }

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

    @Transactional(readOnly = true)
    public UserUsageResponseDto getUserUsage(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));
        return getUserUsageForUser(user);
    }

    @Transactional(readOnly = true)
    public UserUsageResponseDto getUserUsageForUser(User user) {
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found.");
        }
        MembershipTier effectiveTier = resolveEffectiveTier(user);
        LocalDate quotaDate = currentQuotaDate();
        int usedToday = user.getId() != null
                ? quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(user.getId(), quotaDate).orElse(0)
                : 0;
        int safeUsed = Math.max(0, usedToday);

        if (effectiveTier == MembershipTier.ADMIN) {
            return UserUsageResponseDto.builder()
                    .membership(MembershipUsageDto.builder()
                            .tier("ADMIN")
                            .status("ACTIVE")
                            .expiresAt(null)
                            .source("ADMIN Role")
                            .build())
                    .videoAnalysis(VideoAnalysisUsageDto.builder()
                            .used(safeUsed)
                            .limit(null)
                            .remaining(null)
                            .unlimited(true)
                            .resetAt(null)
                            .timezone("Asia/Ho_Chi_Minh")
                            .build())
                    .liveAnalysis(LiveAnalysisUsageDto.builder()
                            .available(true)
                            .unlimited(true)
                            .build())
                    .build();
        }

        boolean adminGrantedPro = hasActiveAdminProGrant(user.getId());
        if (effectiveTier == MembershipTier.PRO && adminGrantedPro) {
            return UserUsageResponseDto.builder()
                    .membership(MembershipUsageDto.builder()
                            .tier("PRO")
                            .status("ACTIVE")
                            .expiresAt(null)
                            .source("Admin Grant")
                            .build())
                    .videoAnalysis(VideoAnalysisUsageDto.builder()
                            .used(safeUsed)
                            .limit(null)
                            .remaining(null)
                            .unlimited(true)
                            .resetAt(null)
                            .timezone("Asia/Ho_Chi_Minh")
                            .build())
                    .liveAnalysis(LiveAnalysisUsageDto.builder()
                            .available(true)
                            .unlimited(true)
                            .build())
                    .build();
        }

        if (effectiveTier == MembershipTier.PRO) {
            ZonedDateTime expiresAt = user.getUpdatedAt() != null
                    ? user.getUpdatedAt().plusDays(30)
                    : (user.getCreatedAt() != null
                            ? user.getCreatedAt().plusDays(30)
                            : ZonedDateTime.now(VIETNAM_ZONE).plusDays(30));

            int limit = 10;
            int remaining = Math.max(0, limit - safeUsed);

            return UserUsageResponseDto.builder()
                    .membership(MembershipUsageDto.builder()
                            .tier("PRO")
                            .status("ACTIVE")
                            .expiresAt(expiresAt)
                            .source("Subscription")
                            .build())
                    .videoAnalysis(VideoAnalysisUsageDto.builder()
                            .used(safeUsed)
                            .limit(limit)
                            .remaining(remaining)
                            .unlimited(false)
                            .resetAt(nextResetAt(quotaDate))
                            .timezone("Asia/Ho_Chi_Minh")
                            .build())
                    .liveAnalysis(LiveAnalysisUsageDto.builder()
                            .available(true)
                            .unlimited(false)
                            .build())
                    .build();
        }

        int limit = 1;
        int remaining = Math.max(0, limit - safeUsed);

        return UserUsageResponseDto.builder()
                .membership(MembershipUsageDto.builder()
                        .tier("FREE")
                        .status("ACTIVE")
                        .expiresAt(null)
                        .source("Default")
                        .build())
                .videoAnalysis(VideoAnalysisUsageDto.builder()
                        .used(safeUsed)
                        .limit(limit)
                        .remaining(remaining)
                        .unlimited(false)
                        .resetAt(nextResetAt(quotaDate))
                        .timezone("Asia/Ho_Chi_Minh")
                        .build())
                .liveAnalysis(LiveAnalysisUsageDto.builder()
                        .available(false)
                        .unlimited(false)
                        .build())
                .build();
    }

    @Transactional(noRollbackFor = QuotaExceededException.class)
    public QuotaEntitlementResponseDto reserveVideoAnalysisQuota(Long userId) {
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found."));

        MembershipTier effectiveTier = resolveEffectiveTier(user);
        LocalDate quotaDate = currentQuotaDate();

        // 1. ADMIN ROLE OR ADMIN-GRANTED PRO UNLIMITED OVERRIDE:
        // Must bypass FREE and PRO daily quota checks entirely.
        // Still records usage and analysis history!
        if (effectiveTier == MembershipTier.ADMIN || hasActiveAdminProGrant(userId)) {
            quotaUsageRepository.insertIfAbsent(userId, quotaDate, UNLIMITED_DAILY_LIMIT_DB_VALUE);
            quotaUsageRepository.incrementUsedCountUnconditional(userId, quotaDate, UNLIMITED_DAILY_LIMIT_DB_VALUE);
            int currentUsed = quotaUsageRepository.findUsedCountByUserIdAndQuotaDate(userId, quotaDate).orElse(0);

            log.debug("Recorded unlimited video analysis quota for userId={} effectiveTier={} usedToday={} date={}",
                    userId, effectiveTier, currentUsed, quotaDate);
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

        if (hasActiveAdminProGrant(userId)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin-granted PRO entitlement cannot be modified by user membership updates."
            );
        }

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
        if (newRole == Role.ROLE_ADMIN || hasActiveAdminProGrant(userId)) {
            quotaUsageRepository.updateDailyLimit(userId, quotaDate, UNLIMITED_DAILY_LIMIT_DB_VALUE);
        } else {
            MembershipTier tier = fallbackTier != null ? fallbackTier : MembershipTier.FREE;
            quotaUsageRepository.updateDailyLimit(userId, quotaDate, tier.getDailyLimit());
        }
    }

    @Transactional
    public void syncQuotaOnEntitlementChange(User user) {
        if (user == null || user.getId() == null) {
            return;
        }
        LocalDate quotaDate = currentQuotaDate();
        if (isUnlimitedAccess(user)) {
            quotaUsageRepository.updateDailyLimit(user.getId(), quotaDate, UNLIMITED_DAILY_LIMIT_DB_VALUE);
        } else {
            MembershipTier effectiveTier = resolveEffectiveTier(user);
            quotaUsageRepository.updateDailyLimit(user.getId(), quotaDate, effectiveTier.getDailyLimit());
        }
    }

    public LocalDate currentQuotaDate() {
        return clock.instant().atZone(VIETNAM_ZONE).toLocalDate();
    }

    public ZonedDateTime nextResetAt(LocalDate quotaDate) {
        return quotaDate.plusDays(1).atStartOfDay(VIETNAM_ZONE);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveAdminProGrant(Long userId) {
        if (membershipEntitlementRepository == null || userId == null) {
            return false;
        }
        return membershipEntitlementRepository.existsByUserIdAndGrantSourceAndStatus(
                userId,
                EntitlementGrantSource.ADMIN_GRANT,
                EntitlementStatus.ACTIVE
        );
    }

    public boolean isUnlimitedAccess(User user) {
        if (user == null) {
            return false;
        }
        return user.getRole() == Role.ROLE_ADMIN || hasActiveAdminProGrant(user.getId());
    }

    public String resolveMembershipSource(User user) {
        if (user != null && user.getRole() == Role.ROLE_ADMIN) {
            return "ADMIN Role";
        }
        if (user != null && hasActiveAdminProGrant(user.getId())) {
            return "Admin Grant";
        }
        if (user != null && user.getMembershipTier() == MembershipTier.PRO) {
            return "Subscription";
        }
        return "Default";
    }

    public MembershipTier resolveEffectiveTier(User user) {
        if (user != null && user.getRole() == Role.ROLE_ADMIN) {
            return MembershipTier.ADMIN;
        }
        if (user != null && hasActiveAdminProGrant(user.getId())) {
            return MembershipTier.PRO;
        }
        return user != null && user.getMembershipTier() != null ? user.getMembershipTier() : MembershipTier.FREE;
    }

    private QuotaEntitlementResponseDto buildEntitlementResponse(User user, LocalDate quotaDate, int usedToday) {
        MembershipTier effectiveTier = resolveEffectiveTier(user);
        boolean adminGrantedPro = hasActiveAdminProGrant(user.getId());
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
                    .grantSource("ADMIN_ROLE")
                    .adminGrantedPro(adminGrantedPro)
                    .timezone(VIETNAM_ZONE.getId())
                    .quotaDate(quotaDate)
                    .resetsAt(nextResetAt(quotaDate))
                    .build();
        }

        if (effectiveTier == MembershipTier.PRO && adminGrantedPro) {
            return QuotaEntitlementResponseDto.builder()
                    .userId(user.getId())
                    .membershipTier(MembershipTier.PRO)
                    .dailyLimit(null)
                    .usedToday(safeUsed)
                    .remainingToday(null)
                    .liveAnalysisUnlocked(true)
                    .unlimited(true)
                    .grantSource(EntitlementGrantSource.ADMIN_GRANT.name())
                    .adminGrantedPro(true)
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
                .grantSource(effectiveTier == MembershipTier.PRO
                        ? EntitlementGrantSource.PAID_SUBSCRIPTION.name()
                        : "DEFAULT")
                .adminGrantedPro(false)
                .timezone(VIETNAM_ZONE.getId())
                .quotaDate(quotaDate)
                .resetsAt(nextResetAt(quotaDate))
                .build();
    }
}
