package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.GettingStartedCampaignEventType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GettingStartedCampaignEventRequest {

    @NotNull(message = "Event type is required")
    private GettingStartedCampaignEventType eventType;

    private Long programId;
}
