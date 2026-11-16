package com.calisthenics.backend.user.dto;

import com.calisthenics.backend.user.MembershipTier;
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
@Schema(description = "Request to update user's membership tier")
public class UpdateMembershipRequest {

    @NotNull(message = "Membership tier is required")
    @Schema(description = "Target membership tier (FREE or PRO)", example = "PRO")
    private MembershipTier membershipTier;
}
