package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.user.OnboardingStatus;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDismissalStatusRequest {
    @NotNull(message = "Status is required")
    private OnboardingStatus status;
}
