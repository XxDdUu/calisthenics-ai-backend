package com.calisthenics.backend.gettingstarted.service;

import com.calisthenics.backend.gettingstarted.dto.AdminGettingStartedProgramRequest;
import com.calisthenics.backend.gettingstarted.dto.EnrollGettingStartedRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignAnalyticsResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignEventRequest;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedCampaignSummaryResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedEnrollPreviewResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedEnrollmentResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramPreviewResponse;
import com.calisthenics.backend.gettingstarted.dto.GettingStartedProgramResponse;
import com.calisthenics.backend.gettingstarted.enums.TargetAudience;
import com.calisthenics.backend.user.OnboardingStatus;
import com.calisthenics.backend.workout.dto.PersistentWorkoutSessionResponse;

import java.time.LocalDate;
import java.util.List;

public interface GettingStartedService {

    // Admin methods
    GettingStartedProgramResponse getAdminProgram();

    GettingStartedProgramResponse createOrUpdateProgram(Long adminId, AdminGettingStartedProgramRequest request);

    GettingStartedProgramResponse createNewProgram(Long adminId, AdminGettingStartedProgramRequest request);

    GettingStartedProgramResponse updateProgramById(Long adminId, Long programId, AdminGettingStartedProgramRequest request);

    GettingStartedProgramResponse getProgramById(Long programId);

    GettingStartedProgramResponse configureAudience(Long adminId, Long programId, TargetAudience targetAudience);

    GettingStartedProgramPreviewResponse previewProgram(Long programId);

    GettingStartedProgramResponse publishProgram(Long adminId);

    GettingStartedProgramResponse publishProgramById(Long adminId, Long programId);

    GettingStartedProgramResponse unpublishProgram(Long adminId);

    GettingStartedProgramResponse unpublishProgramById(Long adminId, Long programId);

    List<GettingStartedCampaignSummaryResponse> getActiveCampaigns();

    GettingStartedCampaignAnalyticsResponse getCampaignAnalytics(Long programId);

    long countEligibleAthletes(TargetAudience targetAudience);

    void deleteProgram(Long adminId);

    // Athlete methods
    GettingStartedProgramResponse getPublishedProgram(Long userId);

    GettingStartedProgramResponse updateDismissalStatus(Long userId, OnboardingStatus status);

    void recordCampaignEvent(Long userId, GettingStartedCampaignEventRequest request);

    GettingStartedEnrollmentResponse enrollAthlete(Long userId);

    GettingStartedEnrollPreviewResponse previewEnrollment(Long userId, LocalDate startDate);

    GettingStartedEnrollmentResponse enrollAthlete(Long userId, EnrollGettingStartedRequest request);

    PersistentWorkoutSessionResponse startDaySession(Long userId, Integer dayNumber, LocalDate workoutDate);

    List<GettingStartedEnrollmentResponse> getUserEnrollments(Long userId);

    GettingStartedEnrollmentResponse completeDay(Long userId, Integer dayNumber);
}
