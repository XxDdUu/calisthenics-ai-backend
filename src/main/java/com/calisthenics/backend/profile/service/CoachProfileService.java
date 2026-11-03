package com.calisthenics.backend.profile.service;

import com.calisthenics.backend.profile.dto.request.CoachProfileRequest;
import com.calisthenics.backend.user.dto.CoachProfileResponse;

import java.util.List;

public interface CoachProfileService {

    CoachProfileResponse getMyCoachProfile(Long userId);

    CoachProfileResponse updateMyCoachProfile(Long userId, CoachProfileRequest request);

    CoachProfileResponse getPublicCoachProfile(Long currentUserIdOrNull, Long coachUserId);

    List<CoachProfileResponse> listPublicCoaches(Long currentUserId);
}
