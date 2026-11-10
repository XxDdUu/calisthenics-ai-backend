package com.calisthenics.backend.profile.dto.response;

import com.calisthenics.backend.user.Gender;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectedCoachSummary {

    private Long coachId;
    private String coachName;
    private String coachAvatarUrl;
    private String coachExperienceLevel;
    private Gender coachGender;
    private String connectionStatus; // ACTIVE
    private Long relationshipId;
    private Long conversationId;
}
