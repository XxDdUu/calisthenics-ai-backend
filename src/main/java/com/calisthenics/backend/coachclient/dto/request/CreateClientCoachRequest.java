package com.calisthenics.backend.coachclient.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateClientCoachRequest {

    @NotNull(message = "Coach ID is required")
    @Positive(message = "Coach ID must be positive")
    private Long coachId;
}
