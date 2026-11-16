package com.calisthenics.backend.videoanalysis.exception;

import com.calisthenics.backend.user.MembershipTier;
import lombok.Getter;

/**
 * Thrown when a FREE tier user attempts to access PRO-only live camera analysis features.
 */
@Getter
public class ProMembershipRequiredException extends RuntimeException {

    private final MembershipTier membershipTier;
    private final boolean liveAnalysisUnlocked;

    public ProMembershipRequiredException(String message, MembershipTier membershipTier, boolean liveAnalysisUnlocked) {
        super(message);
        this.membershipTier = membershipTier;
        this.liveAnalysisUnlocked = liveAnalysisUnlocked;
    }
}
