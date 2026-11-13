package com.calisthenics.backend.gettingstarted.dto;

import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.user.OnboardingStatus;
import lombok.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GettingStartedProgramResponse {
    private Long id;
    private String title;
    private String description;
    private TargetAudience targetAudience;
    private boolean published;
    private Integer version;
    private Long creatorId;
    private String creatorName;
    private ZonedDateTime createdAt;
    private ZonedDateTime updatedAt;
    private ZonedDateTime publishedAt;
    private boolean eligible;
    private OnboardingStatus userDismissalState;
    @Builder.Default
    private List<GettingStartedDayResponse> days = new ArrayList<>();
    private GettingStartedEnrollmentResponse enrollment;
}
