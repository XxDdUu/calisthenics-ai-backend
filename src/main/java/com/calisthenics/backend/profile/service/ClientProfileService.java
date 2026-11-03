package com.calisthenics.backend.profile.service;

import com.calisthenics.backend.profile.dto.request.ClientProfileRequest;
import com.calisthenics.backend.profile.dto.response.ClientProfileResponse;
import com.calisthenics.backend.profile.dto.response.ClientTrainingOverviewResponse;

public interface ClientProfileService {

    ClientProfileResponse getMyClientProfile(Long userId);

    ClientProfileResponse updateMyClientProfile(Long userId, ClientProfileRequest request);

    ClientProfileResponse getClientProfile(Long currentUserId, Long clientUserId);

    ClientTrainingOverviewResponse getClientOverview(Long currentUserId, Long clientUserId);
}
