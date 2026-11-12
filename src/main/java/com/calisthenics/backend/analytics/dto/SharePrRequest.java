package com.calisthenics.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharePrRequest {

    @JsonAlias({"coachId", "coach_id", "recipient_id", "targetUserId"})
    private Long recipientId;

    @Size(max = 64, message = "Client message ID must be 64 characters or fewer")
    private String clientMessageId;
}
