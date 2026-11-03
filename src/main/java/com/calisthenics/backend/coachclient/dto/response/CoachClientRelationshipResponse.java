package com.calisthenics.backend.coachclient.dto.response;

import com.calisthenics.backend.coachclient.type.CoachClientRelationshipStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CoachClientRelationshipResponse {

    private Long id;
    private Long coachId;
    private String coachName;
    private String coachAvatarUrl;
    private String coachExperienceLevel;
    private Long clientId;
    private String clientName;
    private String clientAvatarUrl;
    private String clientExperienceLevel;
    private Long requestedBy;
    private CoachClientRelationshipStatus status;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
}
