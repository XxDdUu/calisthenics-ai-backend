package com.calisthenics.backend.user;

/**
 * Subscription membership tiers controlling daily video analysis quota and live AI access.
 * FREE: 1 accepted video upload per day, no live analysis.
 * PRO: 10 accepted video uploads per day, live analysis enabled.
 * ADMIN: Unlimited video uploads, live analysis enabled, role-based override.
 */
public enum MembershipTier {
    FREE(1, false, false),
    PRO(10, true, false),
    ADMIN(null, true, true);

    private final Integer dailyLimit;
    private final boolean liveAnalysisUnlocked;
    private final boolean unlimited;

    MembershipTier(Integer dailyLimit, boolean liveAnalysisUnlocked, boolean unlimited) {
        this.dailyLimit = dailyLimit;
        this.liveAnalysisUnlocked = liveAnalysisUnlocked;
        this.unlimited = unlimited;
    }

    public Integer getDailyLimit() {
        return dailyLimit;
    }

    public boolean isLiveAnalysisUnlocked() {
        return liveAnalysisUnlocked;
    }

    public boolean isUnlimited() {
        return unlimited;
    }
}
