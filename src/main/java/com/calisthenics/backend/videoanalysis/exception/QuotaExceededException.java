package com.calisthenics.backend.videoanalysis.exception;

import com.calisthenics.backend.videoanalysis.dto.QuotaEntitlementResponseDto;
import lombok.Getter;

/**
 * Thrown when a user attempts to initiate a video analysis after exhausting their daily quota.
 */
@Getter
public class QuotaExceededException extends RuntimeException {

    private final QuotaEntitlementResponseDto quota;

    public QuotaExceededException(String message, QuotaEntitlementResponseDto quota) {
        super(message);
        this.quota = quota;
    }
}
