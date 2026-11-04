package com.calisthenics.backend.analytics.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SharedPersonalRecordResponse {
    private Long id;
    private PersonalRecordResponse personalRecord;
    private Long clientId;
    private String clientName;
    private String clientAvatarUrl;
    private Long coachId;
    private String coachName;
    private ZonedDateTime sharedAt;
    private String coachFeedback;
    private ZonedDateTime feedbackAt;
    private Integer rating;
    private boolean isRead;
    private ZonedDateTime readAt;
    private String status;

    @JsonProperty("clientUserId")
    public Long getClientUserId() {
        return clientId;
    }

    @JsonProperty("coachUserId")
    public Long getCoachUserId() {
        return coachId;
    }
}


