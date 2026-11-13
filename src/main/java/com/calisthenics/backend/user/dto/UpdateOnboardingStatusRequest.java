package com.calisthenics.backend.user.dto;

import com.calisthenics.backend.user.OnboardingStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request to update user's onboarding status")
public class UpdateOnboardingStatusRequest {

    @NotNull(message = "Onboarding status is required")
    @Schema(description = "New onboarding status", example = "COMPACT_BANNER")
    private OnboardingStatus status;
}
