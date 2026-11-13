package com.calisthenics.backend.gettingstarted.service;

import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedEnrollmentResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;

public interface GettingStartedService {

    // Admin methods
    GettingStartedProgramResponse getAdminProgram();

    GettingStartedProgramResponse createOrUpdateProgram(Long adminId, AdminGettingStartedProgramRequest request);

    GettingStartedProgramResponse publishProgram(Long adminId);

    GettingStartedProgramResponse unpublishProgram(Long adminId);

    void deleteProgram(Long adminId);

    // Athlete methods
    GettingStartedProgramResponse getPublishedProgram(Long userId);

    GettingStartedProgramResponse updateDismissalStatus(Long userId, com.calisthenics.backend.user.OnboardingStatus status);

    GettingStartedEnrollmentResponse enrollAthlete(Long userId);

    GettingStartedEnrollmentResponse completeDay(Long userId, Integer dayNumber);
}
